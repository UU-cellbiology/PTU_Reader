/**
 *
 *  PTU_Reader, ImageJ plugin for reading .ptu/.pt3 PicoQuant FLIM image data
 *  
 *  Aim : Open and convert Picoquant .ptu/.pt3 image files for FLIM analysis
 *  Use : Simply select your .ptu/.pt3 file and plugin will provide:
 *  1) Lifetime image stack for each channel (1-4). Each frame corresponds
 *     to the specific lifetime value, intensity of pixel is equal 
 *     to the number of photons with this lifetime (during whole acquisition)
 *  2) Intensity and average lifetime images/stacks. Intensity is just
 *     acquisition image/stack by frame (in photons) and in addition,
 *     plugin generates average lifetime image.
 *     Both can be binned. 
 *
 */

/**
 *
 * @author Francois Waharte, PICT-IBiSA, UMR144 CNRS - Institut Curie, Paris France (2016, pt3 read)
 * @author Eugene Katrukha, Utrecht University, Utrecht, the Netherlands (2026, ptu read, intensity and average lifetime stacks)
 */

package ptureader;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Label;
import java.awt.TextField;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;

import ij.*;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.*;
import ij.util.Tools;


public class PTU_Reader_ implements PlugIn
{
	/** plugin version **/
    String sVersion = "0.2.1";

	 // wraparound constants
    final static int PT3WRAPAROUND = 65536;
    final static int HT3WRAPAROUND = 1024;    
    
    /** Main reading buffer **/
    ByteBuffer bBuff = null;

    /** total number of records **/
    int nRecords = 0;
    
	/** image width**/
	int nPixX = 0;
	
	/** image height**/
	int nPixY = 0;
	
	/** pixel size in um**/
	double dPixSize = 0;
	
	/** Line start marker**/
	int nLineStart = 0;
	
	/** Line end marker**/
	int nLineStop = 0;
	
	/** Frame marker **/
	int nFrameMark = -1;
	
	/** resolution of TCSPC in ns**/
	float fTimeResolution = 0.0f;
	
	/** if Frame marker is present (NOT A RELIABLE MARKER??)**/
	boolean bFrameMarkerPresent = false;
    
    /** bin size in frames**/
    int nTimeBin = 1;
    
    /** show lifetime stack**/
    boolean bLoadLTOrderedStacks = true;
    
    /** show intensity and average lifetime per frame images**/
	boolean bLoadIntAverLTImages = true;
	
    /** bin size in frames**/
    int nTotalBins = 0;
    
    /** array marking presence of channels **/
    boolean [] bChannels = new boolean[4]; 
    
    /** acquisition information **/
    public StringBuilder stringInfo = new StringBuilder();
    
    /** acquisition information **/
    String AcquisitionInfo;
    
    /** flag: whether to load just a range of frames**/
    boolean bLoadRange;
    
    /** min frame number to load**/
    int nFrameMin;
    
    /** max frame number to load**/
    int nFrameMax;
    
    /** total number of Frames in PTU file **/   
    int nTotFrames;
    
    /** Lifetime loading option:
     * 0 = whole stack
     * 1 = use binning **/
    int nLTload;
        
    /** defines record format depending on device (picoharp/hydraharp, etc) **/
    int nRecordType;

    boolean isT2;

    int nHT3Version = 2;
    
    //RECORD READING STATE VARIABLES 
    
    /** current nsync value (without accumulated global time) **/
    int nsync = 0;
    /** channel number or if it is wraparound signal (chan == 15)**/
	int chan = 0;
	/** special marker value (line/frame start/stop)**/
	int markers = 0;
	/** lifetime count **/
	int dtime = 0;
	/** accumulated global time addition **/
	long ofltime;
	
	/** current line (y coordinate )**/
	int curLine; 
	
	/** current pixel's x coordinate **/
	int curPixel = 0;
	
	/** current "global time" = ofltime + nsync**/
	long curSync = 0;
	
	/** "global time" of line start **/
	long syncStart = -1;
	
	/** whether the photon is between start/stop line markers **/
	boolean insideLine = false;
	
	/** maximum time of photon arrival (int) **/
	int dtimemax;
	
	
	/** array of intensity images for each channel **/
	final ImagePlus [] ipInt = new ImagePlus[4];

	/** array of average lifetime images for each channel **/
	final ImagePlus [] ipAverT = new ImagePlus[4];	
	
	/** array of stacks with ordered lifetime images for each channel **/
	final ImagePlus [] ipLTOrdered = new ImagePlus[4];
	
	/** total cumulative photons per channel **/
	final long [][] lPhotCumHistogram = new long[4][];
	
	/** IRF average time estimation per channel **/
	final float [] tZeroIRF = new float[4];
	
	/** make negative lifetime zero **/
	boolean bRemoveNegativeLT = false;
	
	//UI things	
	Choice loadOption;
	TextField tfBin;
	Label lBinFrames;
	GenericDialog loadParamsDialog;
	String [] loadoptions = new String [] {	"Join all frames","Load binned"};
	Color textBGcolor;
	Color textFGcolor;
	
	@Override
	public void run(String arg) 
	{		
		/** current binned frame number **/
		int nBinnedFrameN = 0;

		//*******************************************************************
		// Open .pt3/.ptu file... 
		//*******************************************************************
		String sInputFilenamePath;
		if(arg.equals(""))
		{
		
			OpenDialog opDiag = new OpenDialog("Choose ptu/pt3 files");//,dir);
			if(opDiag.getPath() == null)
				return;
			sInputFilenamePath = opDiag.getPath();
		}
		else
		{
			sInputFilenamePath = arg;
		}
		
		File inputFileName = new File(sInputFilenamePath);

		String filename = inputFileName.getName();
		String extension = filename.substring(filename.length()-3);
		
		if(!(extension.toLowerCase().equals("ptu") || extension.toLowerCase().equals("pt3")))
		{
			IJ.error("Only ptu and pt3 format files are supported!");
			return;
		}

		//initialize reading byte buffer
		try (FileInputStream fis = new FileInputStream(inputFileName);
				FileChannel fc = fis.getChannel(); ) 
		{
			int size = (int)fc.size();
			bBuff = ByteBuffer.allocate(size);

			fc.read(bBuff);
			bBuff.flip();

			System.out.println("file size: " + size);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Buffer position: " + bBuff.position());

		System.out.println("Buffer limit: " + bBuff.limit());
		
		//READING HEADER
		IJ.log("PTU_Reader v." + sVersion );
		stringInfo.append("PTU_Reader v." + sVersion + "\n");
		
		IJ.showStatus("Reading header info...");
		
		//ptu format
		if(extension.toLowerCase().equals("ptu"))
		{
			if(!HeaderReader.readPTUHeader(this))
			return;
		}
		//pt3 format
		if(extension.toLowerCase().equals("pt3"))
		{
			if (!HeaderReader.readPT3Header(this))
				return;
			nRecordType = HeaderReader.rtPicoHarpT3;
		}
		
		//store info
		AcquisitionInfo = "";
		AcquisitionInfo = stringInfo.toString();
		
		//get current data position in the buffer		
		int dataPosition = bBuff.position();
		System.out.println("Data position: " + dataPosition);
		
		//STUB
		//For some reason reading markers with values more that 2
		// is wrong. 
		//temporary stub
		if(nLineStart > 2)
			nLineStart = 4;
		
		if(nLineStop > 2)
			nLineStop = 4;	
		
		if(nFrameMark > 2 && nRecordType == HeaderReader.rtPicoHarpT3)
		{
			nFrameMark = 4;
			bFrameMarkerPresent = true;
		}
		
		if(nFrameMark > 2 && nRecordType != HeaderReader.rtPicoHarpT3)
		{
			//nFrameMark=4;
			bFrameMarkerPresent = true;
		}
		
		//****************************************************
		//****************************************************
		// Read T3 records (the actual data) once
		// to calculate total frame number, maximum lifetime register and syncCountPerLine
		//****************************************************
		//****************************************************
		
		resetReading();
		
		int frameNb = 1;
		long syncCountPerLine = 0;
		int nLines = 0;
		
		dtimemax = Integer.MIN_VALUE;
		
		boolean isPhoton;

		IJ.showStatus("Analyzing average acquisition speed/max time/channels...");
		
		for(int n = 0; n < nRecords; n++)
		{				
			isPhoton = readRecord();
					
			// it is a marker!
			if (!isPhoton)
			{		
				if (markers == nLineStart && syncStart < 0)
				{
					syncStart = ofltime + nsync;
				}
				else
				{
					if ((markers == nLineStop) && (syncStart >= 0))
					{
						syncCountPerLine += ofltime + nsync - syncStart;
						syncStart = -1;
						nLines++;
					}
				}
				if(markers >= nFrameMark && bFrameMarkerPresent) 
				{
					frameNb += 1;
				}
			}
			//it is photon, let's mark channel presence
			else
			{
				bChannels[chan-1] = true;
				
				if(dtime > dtimemax)
					dtimemax = dtime;
			}
			IJ.showProgress(n + 1, nRecords);
		}
		IJ.showProgress(nRecords, nRecords);
		
		//somehow SymPhoTime removes last measurement???
		dtimemax--;
		
		// Get the average sync signals per line in the recorded data
		// It helps to determine photon assignment to a pixel.
		// Is it the best idea? I'm not sure, but it works so far. 
		// In principle, should use line start/end
		syncCountPerLine /= nLines;				
		
		if(!bFrameMarkerPresent)
			frameNb = (int)Math.ceil((double)nLines/(double)nPixY) + 1;

		IJ.log("syncCountPerLine: " + syncCountPerLine);
		IJ.log("Total frames: " + Integer.toString(frameNb-1));
		IJ.log("Maximum time: " + Integer.toString(dtimemax));
		
		nTotFrames = frameNb-1;
		//show user load settings dialog 
		if(!loadDialog())
		{
			bBuff.clear();
			return;
		}
		
		//load range only
		if(!bLoadRange)
		{
			nFrameMin = 1;
			nFrameMax = frameNb-1;
		}
			
		nTotalBins = (int)Math.ceil((double)(nFrameMax-nFrameMin+1)/(double)nTimeBin);

		//prepare output images 
		String shortFilename = inputFileName.getName().split(".pt")[0];
		
		initOutput(shortFilename);
		
		////////////////////////////////////////////////////////
		////// Read the data second time and place it in images
		////////////////////////////////////////////////////////	
		
		IJ.showStatus("Reading lifetime data...");
				
		// initialize/reset read variables		
		resetReading();	
		
		bBuff.position(dataPosition);
		
		boolean frameUpdate = true;	
		
		int nCurrFrame = 1;		
		
		float tempFloat = 0;
		
		int tempInt = 0;
			
		// read data
		for(int n = 0; n < nRecords; n++)
		{	
			
			isPhoton = readRecord();
			
			//update global time
			//this should happen after the readRecord
			curSync = ofltime + nsync;
			
			//it is a marker
			if(!isPhoton)
			{		
				if(markers >= nFrameMark && bFrameMarkerPresent) 
				{
					nCurrFrame += 1;
					frameUpdate = true;
					curLine = 0;
				}
				if (markers == nLineStart && syncStart < 0)
				{
					insideLine = true;
					syncStart = curSync;
				}
				else
				{
					if (markers == nLineStop && syncStart >= 0)
					{
						insideLine = false;
						curLine++;						
						syncStart = -1;
						if(curLine == nPixY && (!bFrameMarkerPresent))
						{
							nCurrFrame += 1;
							curLine = 0;
							frameUpdate = true;
						}
					}
				}
			}
			//it is a photon
			else if (insideLine)
			{				
				curPixel = (int) Math.floor((curSync-syncStart)/(double)syncCountPerLine*nPixX);
				
				if(nCurrFrame >= nFrameMin && nCurrFrame <= nFrameMax)
				{
					if(bLoadIntAverLTImages)
					{
						//frame update
						if(frameUpdate)
						{
							nBinnedFrameN = (int)Math.ceil((double)(nCurrFrame-nFrameMin+1)/(double)nTimeBin);
							//update current frame in the output
							for (int nCh = 0; nCh < 4; nCh++)
							{
								if(bChannels[nCh])
								{
									ipInt[nCh].setSliceWithoutUpdate(nBinnedFrameN);
									ipAverT[nCh].setSliceWithoutUpdate(nBinnedFrameN);
								}						
							}
							frameUpdate = false;
						}
						
						if(dtime <= dtimemax)
						{
							//intensity
							tempFloat = Float.intBitsToFloat(ipInt[chan-1].getProcessor().getPixel(curPixel, curLine));
							tempFloat++;
							ipInt[chan-1].getProcessor().putPixel(curPixel, curLine, Float.floatToIntBits(tempFloat));
	
							//cumulative lifetime
							tempFloat = Float.intBitsToFloat(ipAverT[chan-1].getProcessor().getPixel(curPixel, curLine));	
							tempFloat += dtime; ///cumulative sum
							ipAverT[chan-1].getProcessor().putPixel(curPixel, curLine, Float.floatToIntBits(tempFloat));
							lPhotCumHistogram[chan-1][dtime]++;
						}
					}
					
					//update lifetime ordered stacks 
					if(bLoadLTOrderedStacks)
					{						
						if(nLTload == 0)
						{
							ipLTOrdered[chan-1].setSliceWithoutUpdate(dtime+1);
						}
						else
						{
							ipLTOrdered[chan-1].setPosition(1, dtime+1, nBinnedFrameN);
						}
						tempInt = ipLTOrdered[chan-1].getProcessor().getPixel(curPixel, curLine);
						tempInt++;
						ipLTOrdered[chan-1].getProcessor().putPixel(curPixel, curLine, tempInt);
					}

				}				
			}	
			IJ.showProgress(n + 1, nRecords);
		}// END of the second read record loop///////////////////
		
		IJ.showProgress(nRecords, nRecords);
		IJ.showStatus("Reading lifetime values...done.");
		
		// Clears the byte buffer containing the file data
		bBuff.clear(); 
		
		showCalculateOutput();

	}

	/** show final images **/
	void showCalculateOutput()
	{
		if(bLoadIntAverLTImages)
		{
			final Calibration calIntLT = new Calibration();
			calIntLT.setUnit("um");
			calIntLT.pixelWidth = dPixSize;
			calIntLT.pixelHeight = dPixSize;
			
			estimateIRFZeroTime();
			for(int nCh = 0; nCh < 4; nCh++)
			{
				if(bChannels[nCh])	
				{
					ipInt[nCh].setProperty("Info", AcquisitionInfo);
					ipAverT[nCh].setProperty("Info", AcquisitionInfo);
					if(dPixSize > 0)
					{
						ipInt[nCh].setCalibration(calIntLT);
						ipAverT[nCh].setCalibration(calIntLT);

					}
					//calculate average
					for(int nFrame = 1; nFrame <= nTotalBins; nFrame++)
					{
						ipAverT[nCh].setSliceWithoutUpdate(nFrame);
						ipInt[nCh].setSliceWithoutUpdate(nFrame);
						for(int x = 0; x<nPixX;x++)
							for(int y = 0; y<nPixY;y++)
							{
								final float fPhotons = ipInt[nCh].getProcessor().getf(x,y);
								final float fSum = ipAverT[nCh].getProcessor().getf(x,y);
								if(fPhotons > 0)
								{
									float fLTCorrected = (fTimeResolution*fSum/fPhotons) - tZeroIRF[nCh];
									//if(fLTCorrected > 0.0f )
									//{
									if(bRemoveNegativeLT)
									{
										if(fLTCorrected<0.0f)
											fLTCorrected = 0.0f;
									}								
									ipAverT[nCh].getProcessor().setf(x, y, fLTCorrected);
									//}
								}
								//should be already zero otherwise
							}
					}
					
					ipInt[nCh].setSliceWithoutUpdate(1);
					ipAverT[nCh].setSliceWithoutUpdate(1);
					ipInt[nCh].show();
					IJ.run(ipInt[nCh], "Enhance Contrast", "saturated=0.35");
					ipAverT[nCh].show();
					IJ.run(ipAverT[nCh], "Enhance Contrast", "saturated=0.35");
				}
			}
		}
		
		//set scale, add info and show lifetime ordered images
		if(bLoadLTOrderedStacks)
		{
			final Calibration calLTOrder = new Calibration();
			calLTOrder.setXUnit("um");
			calLTOrder.setYUnit("um");
			calLTOrder.setZUnit("ns");
			calLTOrder.pixelWidth = dPixSize;
			calLTOrder.pixelHeight = dPixSize;
			calLTOrder.pixelDepth = fTimeResolution;
			for(int nCh = 0; nCh < 4; nCh++)
				if(bChannels[nCh])
				{
					//metadata
					ipLTOrdered[nCh].setProperty("Info", AcquisitionInfo); 
					//image calibration
					if(dPixSize>0)
					{
						ipLTOrdered[nCh].setCalibration(calLTOrder);
					}					

					ipLTOrdered[nCh].show();
					IJ.run(ipLTOrdered[nCh], "Enhance Contrast", "saturated=0.35");
				}
		}
	}
	
	/** initializes output images/stacks **/
	void initOutput(String shortFilename)
	{

		for (int nCh = 0; nCh < 4; nCh++)
			if(bChannels[nCh])				
			{	
				String sChannel = "_C" + Integer.toString(nCh+1);
				if(bLoadIntAverLTImages)
				{
					lPhotCumHistogram[nCh] = new long[dtimemax+1];
					//intensity and lifetime
					if(bLoadIntAverLTImages)
					{
						String sIntTitle = shortFilename + sChannel + "_Intensity";
						String sFLTtitle = shortFilename + sChannel + "_FastLifeTime";
						if(nLTload == 1)
						{
							sIntTitle = sIntTitle + "_Bin="	+ Integer.toString(nTimeBin);
							sFLTtitle = sFLTtitle + "_Bin="	+ Integer.toString(nTimeBin);
						}
						ipInt[nCh] = IJ.createImage(sIntTitle, "32-bit black", nPixX, nPixY, nTotalBins);
						ipAverT[nCh] = IJ.createImage(sFLTtitle, "32-bit black", nPixX, nPixY, nTotalBins);	
					}
				}
				
				//lifetime ordered
				if(bLoadLTOrderedStacks)
				{
					String sLTtitle = shortFilename + sChannel + "_LifetimeStack";
					if(nLTload == 1)
					{
						sLTtitle = sLTtitle +"_Bin=" + Integer.toString(nTimeBin);
					}
					
					try 
					{											
						if(nLTload == 0)
						{
							ipLTOrdered[nCh] = IJ.createImage(sLTtitle, "8-bit black", nPixX, nPixY, dtimemax + 1);
						}
						else
						{
							ipLTOrdered[nCh] = IJ.createHyperStack(sLTtitle, nPixX, nPixY, 1, dtimemax+1, nTotalBins, 8);							
						}
					}

					catch (Exception e) 
					{
						e.printStackTrace();
					} 
					catch (OutOfMemoryError e) 
					{
						IJ.log("Unable to allocate memory for lifetime stack (out of memory)!!\n Skipping lifetime loading.");
						bLoadLTOrderedStacks = false;
					}
				}

			}
		
	}
	
	void estimateIRFZeroTime()
	{
		for(int nCh = 0; nCh < 4; nCh++)
		{
			if(bChannels[nCh])	
			{
				//calculate derivative of total photon histogram
				for (int t = 0; t < dtimemax; t++)
				{
					lPhotCumHistogram[nCh][t] =lPhotCumHistogram[nCh][t+1]-lPhotCumHistogram[nCh][t]; 					
				}
				//find maximum
				long lMax = Long.MAX_VALUE*(-1);
				int maxInd = 0;
				for(int t=0; t < dtimemax; t++)
				{
					if(lPhotCumHistogram[nCh][t]>lMax)
					{
						lMax = lPhotCumHistogram[nCh][t];
						maxInd = t;
					}
				}
				// peak position centroid estimate
				// according to https://iopscience.iop.org/article/10.3847/2515-5172/aae265
				// A Robust Method to Measure Centroids of Spectral Lines
				// Richard Teague  and Daniel Foreman-Mackey
				// DOI 10.3847/2515-5172/aae265
				//estimate
				final float fMax = ( float ) ( maxInd - 0.5*(lPhotCumHistogram[nCh][maxInd+1]-lPhotCumHistogram[nCh][maxInd-1])/(lPhotCumHistogram[nCh][maxInd+1]+lPhotCumHistogram[nCh][maxInd-1]-2.0f*lPhotCumHistogram[nCh][maxInd]) );	
				tZeroIRF[nCh] = fTimeResolution*(fMax+1.0f);
				IJ.log("Estimated IRF t=0 for channel "+Integer.toString( nCh )+": "+Float.toString( tZeroIRF[nCh] )+"ns");
			}
		}
	}
	
    public static int hex2dec(String s) 
    {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = 16 * val + d;
        }
        return val;
    }
    
	
	/** function reads one 4 byte record from the file
	 *  into the current state variables. 
	 *  Returns true if it is a photon and false if it is a marker. **/
	boolean readRecord()
	{
		final byte[] record = new byte[4];
		bBuff.get(record,0,4);
		
		final int recordData = ((record[3] & 0xFF) << 24) | ((record[2] & 0xFF) << 16) | ((record[1] & 0xFF) << 8) | (record[0] & 0xFF); //Convert from little endian

		//picoharp
		if(nRecordType == HeaderReader.rtPicoHarpT3)
		{
			return ReadPT3(recordData);
		}
		//multiharp
		return ReadHT3(recordData);
	}
	
	/** returns true if it is a photon data, returns false if it is a marker **/
	boolean ReadPT3(final int recordData)//, long nsync, int dtime, int chan, int markers)
	{
		boolean isPhoton = true;
		nsync = recordData&0xFFFF; //lowest 16 bits
		dtime = (recordData>>>16)&0xFFF;
		chan = (recordData>>>28)&0xF;

		if (chan == 15)
		{	
			isPhoton = false;
			markers = (recordData>>16)&0xF;			
			if(markers == 0 || dtime == 0)
			{
				ofltime += PT3WRAPAROUND;
			}
		}
		return isPhoton;
	}
	
	/** returns true if it is a photon data, returns false if it is a marker **/
	boolean ReadHT3(final int recordData)//, long nsync, int dtime, int chan, int markers)
	{
		int special;
	
		boolean isPhoton=true;
		nsync = recordData&0x3FF;//lowest 10 bits
		dtime = (recordData>>>10)&0x7FFF;
		chan = (recordData>>>25)&0x3F;
		special = (recordData>>>31)&0x1;
		special = special * chan;
		
		if (special == 0)
		{
			/*if(chan==0)
				return false;
			else
			*/
			chan = chan + 1;
			return isPhoton;
		}
		isPhoton = false;
		if(chan == 63)
		{
			if(nsync==0 || nHT3Version == 1)
				ofltime = ofltime + HT3WRAPAROUND;
			else
				ofltime = ofltime + HT3WRAPAROUND * nsync;
		}
		
		if ((chan >= 1) && (chan <= 15)) // these are markers
		{
				markers = chan;
		}					
		
		return isPhoton;
	}
	
	void resetReading()
	{
		ofltime = 0;
	    curLine = 0;
		curSync = 0;
		syncStart = -1;
		nsync = 0;
		chan = 0;
		dtime = 0;
		markers = 0;
		curPixel = 0;
		insideLine = false;
	}	
	
    /** 
	 * Dialog displaying options for loading
	 * **/
	public boolean loadDialog() 
	{
		loadParamsDialog = new GenericDialog("FLIM data load parameters");
			
		loadParamsDialog.addMessage("Total number of frames: " + Integer.toString(nTotFrames) );
		loadParamsDialog.addCheckbox("Show Intensity and FastLifetime", Prefs.get("PTU_Reader.bIntLTImages", true));
		loadParamsDialog.addCheckbox("Show Lifetime raw stack", Prefs.get("PTU_Reader.bLTOrder", false));
		loadParamsDialog.addMessage("\n");	
		loadParamsDialog.addChoice("Output:", loadoptions, Prefs.get("PTU_Reader.IntFLTload", "Join all frames"));
		loadParamsDialog.addNumericField("Bin frames:", Prefs.get("PTU_Reader.nTimeBin", 1), 0);
		lBinFrames = loadParamsDialog.getLabel();
		loadParamsDialog.addCheckbox("Load only frame range (applies to all)", Prefs.get("PTU_Reader.bLoadRange", false));
		loadParamsDialog.addStringField("Range:", new DecimalFormat("#").format(1) + "-" +  new DecimalFormat("#").format(nTotFrames));		
		loadParamsDialog.addCheckbox("Remove negative FastLifetime", Prefs.get("PTU_Reader.bRemoveNegativeLT", false));
		
		loadOption = ( Choice ) loadParamsDialog.getChoices().get( 0 );
		tfBin = ( TextField ) loadParamsDialog.getNumericFields().get( 0 );
		textFGcolor = tfBin.getForeground();
		textBGcolor = tfBin.getBackground();
		//init
		if(Prefs.get("PTU_Reader.IntFLTload", "Join all frames").equals( "Join all frames" ))
		{
			lBinFrames.setText( "No binning" );
			tfBin.setEnabled( false );
			tfBin.setBackground( Color.GRAY );
			tfBin.setForeground( Color.GRAY );

		}
		loadParamsDialog.addMessage("\n");

			
		loadParamsDialog.addDialogListener( (gd,event)-> dialogItemChanged(gd,event));
		loadParamsDialog.setResizable(false);
		loadParamsDialog.showDialog();
		if (loadParamsDialog.wasCanceled())
	        return false;


		return true;
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) 
	{
		if(e!=null)
		{
			if(e.getSource() == loadOption)
			{
				if(loadOption.getSelectedIndex() == 0)
				{
					lBinFrames.setText( "No binning" );
					tfBin.setEnabled( false );
					tfBin.setBackground( Color.GRAY );
					tfBin.setForeground( Color.GRAY );
				}
				else
				{
					lBinFrames.setText( "Bin frames:" );
					tfBin.setBackground( textBGcolor );
					tfBin.setForeground( textFGcolor );
					tfBin.setEnabled( true );
				}
			}
		}
		if(gd.wasOKed())
		{
			readDialogParameters();
		}			
		
		return true;
	}
	
	void readDialogParameters()
	{
		bLoadIntAverLTImages = loadParamsDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bIntLTImages", bLoadIntAverLTImages);	
		
		bLoadLTOrderedStacks = loadParamsDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bLTOrder", bLoadLTOrderedStacks);
		
		nLTload = loadParamsDialog.getNextChoiceIndex();
		Prefs.set("PTU_Reader.LTload", loadoptions[nLTload]);
		
		nTimeBin = (int)loadParamsDialog.getNextNumber();
		if(nTimeBin < 1 || nTimeBin > nTotFrames)
		{
			IJ.log("Bin size should be in the range from 1 to total frame size, resetting to 1");
			nTimeBin = 1;
		}
		
		Prefs.set("PTU_Reader.nTimeBin", nTimeBin);
		
		bLoadRange = loadParamsDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bLoadRange", bLoadRange);	
		if(!bLoadRange)
		{
			 nFrameMin = 1;
			 nFrameMax = nTotFrames;
		}
		else
		{
			//range of frames		
			String sFrameRange = loadParamsDialog.getNextString();
			Prefs.set("PTU_Reader.sFrameRange", sFrameRange);	
			String[] range = Tools.split(sFrameRange, " -");
			double c1 = loadParamsDialog.parseDouble(range[0]);
			double c2 = range.length == 2 ? loadParamsDialog.parseDouble(range[1]) : Double.NaN;
			nFrameMin = Double.isNaN(c1)?1:(int)c1;
			nFrameMax = Double.isNaN(c2)?nFrameMin:(int)c2;
			if (nFrameMin < 1) nFrameMin = 1;
			if (nFrameMax > nTotFrames) nFrameMax = nTotFrames;
			if (nFrameMin > nFrameMax) 
			{
				nFrameMin = 1; 
				nFrameMax = nTotFrames;
			}	

		}	
		
		if(nLTload == 0)
		{
			nTimeBin = nFrameMax - nFrameMin + 1;
		}
		
		bRemoveNegativeLT = loadParamsDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bRemoveNegativeLT", bRemoveNegativeLT);	
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		PTU_Reader_ read = new PTU_Reader_();
		//read.run("/home/eugene/Desktop/projects/PTU_reader/20231117_image_sc/Example_image.sc.ptu" );
		//read.run("/home/eugene/Desktop/projects/PTU_reader/20250818_Falcon/AcGFP_s1_seq1.ptu");
		read.run("/home/eugene/Desktop/projects/PTU_reader/20260108_picoharp300/default_005.ptu");
		//read.run("");
	}

}