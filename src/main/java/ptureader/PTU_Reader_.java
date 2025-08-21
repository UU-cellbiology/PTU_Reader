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
 *     
    License:
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

/**
 *
 * @author Francois Waharte, PICT-IBiSA, UMR144 CNRS - Institut Curie, Paris France (2016, pt3 read)
 * @author Eugene Katrukha, Utrecht University, Utrecht, the Netherlands (2017, ptu read, intensity and average lifetime stacks)
 */

package ptureader;

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


public class PTU_Reader_ implements PlugIn{

	/** plugin version **/
    String sVersion = "0.2.0";

	 // wraparound constants
    final static int WRAPAROUND = 65536;
    final static int T3WRAPAROUND = 1024;    
    
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
    
    /** Lifetime loading option:
     * 0 = whole stack
     * 1 = use binning **/
    int nLTload;
        
    /** defines record format depending on device (picoharp/hydraharp, etc) 
     * **/
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
	
	/** current line (y -coordinate )**/
	int curLine; 
	/** current pixel x -coordinate **/
	int curPixel = 0;
	
	/** current "global time" = ofltime+ nsync**/
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
		
		
		//show user load settings dialog 
		if(!loadDialog(frameNb-1))
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
		
		initOutputImages(shortFilename);
		
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
			//update global time
			curSync = ofltime + nsync;
			
			isPhoton = readRecord();
			
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
						if(curLine == (nPixY)&&(!bFrameMarkerPresent))
						{
							nCurrFrame += 1;
							curLine = 0;
							frameUpdate = true;
						}
					}
				}
			//it is a photon
			}
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

						//intensity
						tempFloat = Float.intBitsToFloat(ipInt[chan-1].getProcessor().getPixel(curPixel, curLine));
						tempFloat++;
						ipInt[chan-1].getProcessor().putPixel(curPixel, curLine, Float.floatToIntBits(tempFloat));

						//cumulative lifetime
						tempFloat = Float.intBitsToFloat(ipAverT[chan-1].getProcessor().getPixel(curPixel, curLine));	
						tempFloat += dtime; ///cumulative sum
						ipAverT[chan-1].getProcessor().putPixel(curPixel, curLine, Float.floatToIntBits(tempFloat));

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
								float fPhotons = ipInt[nCh].getProcessor().getf(x,y);
								float fSum = ipAverT[nCh].getProcessor().getf(x,y);
								if(fPhotons > 0)
								{
									ipAverT[nCh].getProcessor().setf(x, y, fTimeResolution*fSum/fPhotons);
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
	void initOutputImages(String shortFilename)
	{
		if(bLoadIntAverLTImages)
		{
			for (int nCh = 0; nCh < 4; nCh++)
				if(bChannels[nCh])				
				{					
					//intensity and lifetime
					if(bLoadIntAverLTImages)
					{
						ipInt[nCh] = IJ.createImage(shortFilename + "_C" + 
								Integer.toString(nCh+1) + "_Intensity_Bin="
								+ Integer.toString(nTimeBin), 
								"32-bit black", nPixX, nPixY, nTotalBins);
						ipAverT[nCh] = IJ.createImage(shortFilename+"_C" + Integer.toString(nCh+1)
						+ "_FastLifeTime_Bin=" + Integer.toString(nTimeBin), 
						"32-bit black", nPixX, nPixY, nTotalBins);	
					}
					
					//lifetime ordered
					if(bLoadLTOrderedStacks)
					{
						try 
						{											
							if(nLTload == 0)
							{
								ipLTOrdered[nCh] = IJ.createImage(shortFilename+"_C" + Integer.toString(nCh + 1)
															+ "LifetimeStack", "8-bit black", nPixX, nPixY, dtimemax + 1);
							}
							else
							{
								String sLTtitle = shortFilename + "_C" + Integer.toString(nCh+1)
												+"_LifetimeStack_Bin=" + Integer.toString(nTimeBin);
								
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
    
    /** 
	 * Dialog displaying options for loading
	 * **/
	public boolean loadDialog(int nTotFrames) 
	{
		GenericDialog loadDialog = new GenericDialog("FLIM data load parameters");
		
		String [] loadoptions = new String [] {
				"Load whole stack","Load binned"};
		
		loadDialog.addMessage("Total number of frames: " + Integer.toString(nTotFrames) );
		loadDialog.addCheckbox("Load Lifetime ordered stack", Prefs.get("PTU_Reader.bLTOrder", true));
		loadDialog.addChoice("Load option:", loadoptions, Prefs.get("PTU_Reader.LTload", "Load whole stack"));
		loadDialog.addMessage("Loading binned results generates large files");
		loadDialog.addMessage("\n");
		loadDialog.addCheckbox("Load Intensity and Lifetime Average stacks", Prefs.get("PTU_Reader.bIntLTImages", true));
		
		loadDialog.addNumericField("Bin size in frames", Prefs.get("PTU_Reader.nTimeBin", 1), 0, 4, " frames");
		loadDialog.addMessage("\n");
		loadDialog.addCheckbox("Load only frame range (applies to all stacks)", Prefs.get("PTU_Reader.bLoadRange", false));
		if(Prefs.get("PTU_Reader.bLoadRange", false))
			loadDialog.addStringField("Range:", Prefs.get("PTU_Reader.sFrameRange", "1 - 2"));		
		else
			loadDialog.addStringField("Range:", new DecimalFormat("#").format(1) + "-" +  new DecimalFormat("#").format(nTotFrames));		

		loadDialog.setResizable(false);
		loadDialog.showDialog();
		if (loadDialog.wasCanceled())
	        return false;
		
		bLoadLTOrderedStacks = loadDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bLTOrder", bLoadLTOrderedStacks);
		nLTload = loadDialog.getNextChoiceIndex();
		Prefs.set("PTU_Reader.LTload", loadoptions[nLTload]);
		
		bLoadIntAverLTImages = loadDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bIntLTImages", bLoadIntAverLTImages);		
		
		nTimeBin = (int)loadDialog.getNextNumber();
		if(nTimeBin < 1 || nTimeBin > nTotFrames)
		{
			IJ.log("Bin size should be in the range from 1 to total frame size, resetting to 1");
			nTimeBin = 1;
		}
		Prefs.set("PTU_Reader.nTimeBin", nTimeBin);		
		
		bLoadRange = loadDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bLoadRange", bLoadRange);	
		
		//range of frames		
		String sFrameRange = loadDialog.getNextString();
		Prefs.set("PTU_Reader.sFrameRange", sFrameRange);	
		String[] range = Tools.split(sFrameRange, " -");
		double c1 = loadDialog.parseDouble(range[0]);
		double c2 = range.length==2?loadDialog.parseDouble(range[1]):Double.NaN;
		nFrameMin = Double.isNaN(c1)?1:(int)c1;
		nFrameMax = Double.isNaN(c2)?nFrameMin:(int)c2;
		if (nFrameMin<1) nFrameMin = 1;
		if (nFrameMax>nTotFrames) nFrameMax = nTotFrames;
		if (nFrameMin>nFrameMax) {nFrameMin=1; nFrameMax=nTotFrames;}	
		
		return true;
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

		if (chan== 15)
		{	
			isPhoton = false;
			markers = (recordData>>16)&0xF;			
			if(markers == 0 || dtime == 0)
			{
				ofltime += WRAPAROUND;
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
				ofltime = ofltime + T3WRAPAROUND;
			else
				ofltime = ofltime + T3WRAPAROUND*nsync;
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
	
	public static void main( final String[] args )
	{
		new ImageJ();
		PTU_Reader_ read = new PTU_Reader_();
		read.run("/home/eugene/Desktop/projects/PTU_reader/20231117_image_sc/Example_image.sc.ptu" );
		//read.run("");
	}

}