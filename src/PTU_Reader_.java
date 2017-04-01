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



import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;

import java.text.SimpleDateFormat;
import java.util.Date;

import ij.*;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.*;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class PTU_Reader_ implements PlugIn{

	 // some type field constants
    final static int tyEmpty8 	   = -65528;//= hex2dec("FFFF0008");
    final static int tyBool8       = 8;// = hex2dec("00000008");
    final static int tyInt8        = 268435464;//hex2dec("10000008");
    final static int tyBitSet64    = 285212680;//hex2dec("11000008");
    final static int tyColor8      = 301989896;//hex2dec("12000008");
    final static int tyFloat8      = 536870920;//hex2dec("20000008");
    final static int tyTDateTime   = 553648136;//hex2dec("21000008");
    final static int tyFloat8Array = 537001983;//hex2dec("2001FFFF");
    final static int tyAnsiString  = 1073872895;//hex2dec("4001FFFF");
    final static int tyWideString  = 1073938431;//hex2dec("4002FFFF");
    final static int tyBinaryBlob  = -1;//hex2dec("FFFFFFFF");
    final static int WRAPAROUND = 65535;

    /** Main reading buffer **/
    ByteBuffer bBuff=null;

    /** total number of records **/
    int Records=0;
	/** image width**/
	int PixX=0;
	/** image height**/
	int PixY=0;
	/** pixel size in um**/
	double dPixSize=0;
    
    /**bin size in frames**/
    int nTimeBin=1;
    /**show lifetime stack**/
    boolean bLTOrder=true;
    /**show intensity and average lifetime per frame images**/
	boolean bIntLTImages=true;
    /**bin size in frames**/
    int nTotalBins=0;
    boolean [] bDataPresent = new boolean[4]; 
    
    /** acquisition information **/
    StringBuilder stringInfo = new StringBuilder();
    /** acquisition information **/
    String AcquisitionInfo;
	
	/**
	 * @param args the command line arguments
	 */
	public void run(String arg) {

		
		int nBinnedFrameN=0;
		int i;
		Calibration cal;

		//*******************************************************************
		// Open .pt3/.ptu file... 
		//*******************************************************************

		//String dir=Prefs.get("PTU_Reader.LastDir",System.getProperty("user.dir"));
		String dir=System.getProperty("user.dir");
		OpenDialog opDiag= new OpenDialog("Choose ptu/pt3 files");//,dir);
		if(opDiag.getPath()==null)
			return;
		File inputFileName=new File(opDiag.getPath());
		//opDiag.
		/*
		JFileChooser chooser = new JFileChooser();
		FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"Picoquant .ptu files", "ptu");
		chooser.setFileFilter(filter);
		chooser.addChoosableFileFilter(new FileNameExtensionFilter("Picoquant .pt3 files", "pt3"));
		String dir=Prefs.get("PTU_Reader.LastDir",System.getProperty("user.dir"));
		File dir2=new File(dir);
		chooser.setCurrentDirectory(dir2);
		int returnVal = chooser.showOpenDialog(chooser);
		File inputFileName=null;
		if(returnVal == JFileChooser.APPROVE_OPTION) {	           
			inputFileName=chooser.getSelectedFile();
			System.out.println("You chose to open this file: " +inputFileName.getName());
		}else{return ;}
		//*/

		String filename=inputFileName.getName();
		String extension=filename.substring(filename.length()-3);
		if(!(extension.toLowerCase().equals("ptu")||extension.toLowerCase().equals("pt3")))
		{
			IJ.error("Only ptu and pt3 format files are supported!");
			return;
		}
		
		FileInputStream fis;
		FileChannel fc;

		try {

			fis = new FileInputStream(inputFileName);

			fc = fis.getChannel();
			int size = (int)fc.size();
			bBuff = ByteBuffer.allocate(size);

			fc.read(bBuff);
			bBuff.flip();

			System.out.println("file size: " + size);
			Prefs.set("PTU_Reader.LastDir",inputFileName.getPath());
			//	tabByte= bBuff.array();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Buffer position: " + bBuff.position());

		System.out.println("Buffer limit: " + bBuff.limit());
		
		//READING HEADER
		IJ.log("PTU_Reader v.0.0.1");
		stringInfo.append("PTU_Reader v.0.0.1\n");
		//ptu format
		if(extension.toLowerCase().equals("ptu"))
		{
			if(!readPTUHeader())
			return;
		}
		//pt3 format
		if(extension.toLowerCase().equals("pt3"))
		{
			if (!readPT3Header())
				return;
		}
		
		//store info
		AcquisitionInfo="";
		AcquisitionInfo=stringInfo.toString();
		
		//get current data position in the buffer		
		int dataPosition=bBuff.position();
		System.out.println("Data position: " + dataPosition);
		
		
		
		//****************************************************
		//****************************************************
		// Read T3 records (the actual data !)
		// calculates frame number and syncCountPerLine
		//****************************************************
		//****************************************************
	//	int cnt_1=0, cnt_2=0, cnt_3=0,cnt_4=0; // Counters for 1-4 channels
		int markers =0;
		int frameNb =0;
		long ofltime=0;
		int curLine=0;
		long curSync=0;
		long syncStart=0;
		long nsync=0;
		int chan=0;
		int recordData =0;
		int curPixel=0;
		long syncCountPerLine=0;
		int nLines=0;
		int dtime=0;
		Boolean frameStart=false;
		Boolean insideLine=false;
		
		long sync_start_count=0;
		
		for(int n=0;n<Records;n++){	
			byte[] record=new byte[4];
			bBuff.get(record,0,4);
			recordData = ((record[3] & 0xFF) << 24) | ((record[2] & 0xFF) << 16) | ((record[1] & 0xFF) << 8) | (record[0] & 0xFF); //Convert from little endian

			nsync= recordData&0xFFFF;
			dtime=(recordData>>>16)&0xFFF;
			chan=(recordData>>>28)&0xF;

			if (chan== 15){		
				if(dtime==0){
					ofltime+=WRAPAROUND;
				}
				else{
					markers =(recordData>>16)&0xF;

					if(markers==4) 
					{
						//count number of frames
						frameNb+= 1;
					}
					if (markers==1)
					{
						sync_start_count=ofltime+nsync;
						//System.out.println("sync_start_count "+sync_start_count);
					}
					if ((markers==2)&&(sync_start_count>0)){
						//System.out.println("current count of nsync " +ofltime+nsync);
						syncCountPerLine+=ofltime+nsync-sync_start_count;
						nLines++;
					}
				}
			}

		}
		syncCountPerLine/=nLines;				// Get the average sync signals per line in the recorded data
		
		//System.out.println("syncCountPerLine "+syncCountPerLine);
		IJ.log("syncCountPerLine: "+syncCountPerLine);
		IJ.log("Total frames: "+Integer.toString(frameNb-1));
		if(!loadDialog(frameNb-1))
		{
			bBuff.clear();
			return;
		}
		nTotalBins=(int)Math.ceil((double)(frameNb-1)/(double)nTimeBin);
		
		//initialize read variables
		markers =0;		 
		ofltime=0;
		curLine=0;
		curSync=0;
		syncStart=0;
		nsync=0;
		chan=0;
		recordData =0;
		curPixel=0;
		nLines=0;
		dtime=0;
		frameStart=false;
		insideLine=false;
		boolean frameUpdate=false;
		sync_start_count=0;
		

		String shortFilename="";
		shortFilename=inputFileName.getName();

		String[] parts =shortFilename.split(".pt");
		shortFilename=parts[0];

		
		////////////////////////////////////////////////////////
		////// Determines the number of channel containing data
		////// and generated intensity 32-bit image stack
		////////////////////////////////////////////////////////
		
		IJ.showStatus("Reading intensity values...");
		bBuff.position(dataPosition);
		
		int [] dataCh = new int[4];
		int datax=0;
		/** array of intensity images for each channel **/
		ImagePlus [] impInt=new ImagePlus[4];
	
		
		int nCurrFrame=0;
		float tempval=0;
		for(int n=0;n<Records;n++){	
			byte[] record=new byte[4];
			bBuff.get(record,0,4);
			 recordData = ((record[3] & 0xFF) << 24) | ((record[2] & 0xFF) << 16) | ((record[1] & 0xFF) << 8) | (record[0] & 0xFF); //Convert from little endian

			// Uncomment to display binary representation of records
			//		System.out.println(" recordInt2 binary: "+Integer.toBinaryString(recordInt2));
			//		System.out.println(" recordInt2 bitcount: "+Integer.bitCount(recordInt2));

			 nsync= recordData&0xFFFF;
			//	System.out.println(" nsync: "+nsync);
			//		System.out.println(" nsync binary: "+Integer.toBinaryString(nsync));
			//		System.out.println(" nsync bitcount: "+Integer.bitCount(nsync));

			dtime=(recordData>>>16)&0xFFF;
			//System.out.println(" dtime: "+dtime);
			//			System.out.println(" dtime binary: "+Integer.toBinaryString(dtime));
			//			System.out.println(" dtime bitcount: "+Integer.bitCount(dtime));
			// 

			 chan=(recordData>>>28)&0xF;
			//System.out.println(" chan: "+chan);
			//		System.out.println(" chan binary: "+Integer.toBinaryString(chan));
			//		System.out.println(" chan bitcount: "+Integer.bitCount(chan));

			curSync=ofltime+nsync;

			if (chan== 15){		

				if(dtime==0){
					ofltime+=WRAPAROUND;
				}
				else{
					markers =(recordData>>16)&0xF;

					if(markers==4) {
						nCurrFrame+= 1;
						frameStart=true;
						frameUpdate=true;
						curLine=0;
					}
					if (markers==1&&frameStart){
						insideLine=true;
						syncStart=curSync;
					}
					if (markers==2){
						insideLine=false;
						curLine++;						
					}
				}

			}else if (insideLine){
				curPixel=(int) Math.floor((curSync-syncStart)/(double)syncCountPerLine*PixX);

				dataCh[chan-1]++;
				//read intensity values
				if(!bDataPresent[chan-1])
				{
					bDataPresent[chan-1]=true;
					impInt[chan-1]=IJ.createImage(shortFilename+"_C"+Integer.toString(chan)+"_Intensity_Bin="+Integer.toString(nTimeBin), "32-bit black", PixX,PixY, nTotalBins);
				}
				if(frameUpdate)
				{
					nBinnedFrameN=(int)Math.ceil((double)nCurrFrame/(double)nTimeBin);
					impInt[chan-1].setSliceWithoutUpdate(nBinnedFrameN);
					frameUpdate=false;
				}
				tempval=Float.intBitsToFloat(impInt[chan-1].getProcessor().getPixel(curPixel, curLine));
				tempval++;
				impInt[chan-1].getProcessor().putPixel(curPixel, curLine, Float.floatToIntBits(tempval));
				
			}	
			IJ.showProgress(n+1, Records);
		}// END of read record loop///////////////////

		
		if(bIntLTImages)
		{
			for(i=0;i<4;i++)
			{
				if(bDataPresent[i])	
				{
					impInt[i].setProperty("Info", AcquisitionInfo);
					//image scale
					if(dPixSize>0)
					{
						cal = impInt[i].getCalibration();
						cal.setUnit("um");
						cal.pixelWidth=dPixSize;
						cal.pixelHeight=dPixSize;
						impInt[i].setCalibration(cal);
					}					
					impInt[i].show();
				}
			}
		}

		//initialize read variables
		markers =0;	
		ofltime=0;
		curLine=0;
		curSync=0;
		syncStart=0;
		nsync=0;
		chan=0;
		recordData =0;
		curPixel=0;
		nLines=0;
		dtime=0;
		frameStart=false;
		insideLine=false;
		sync_start_count=0;
		nCurrFrame=0;
		
		////////////////////////////////////////////////////////
		////// Get data and place them in images
		////////////////////////////////////////////////////////
		
		/** array of stacks with ordered lifetime images for each channel **/
		ImagePlus [] impCh = new ImagePlus[4];
		/** array of average lifetime images for each channel **/
		ImagePlus [] impAverT = new ImagePlus[4];

		
		
		//////////////////////////////////////////////////
		// Warning 8-bit images !!!!!!!!!!!!!!!!!!!!!!
		/////////////////////////////////////////////////////
		if(bLTOrder)
		{
			for(i=0;i<4;i++)
				if(bDataPresent[i])
					impCh[i]=IJ.createImage(shortFilename+"_C"+Integer.toString(i+1)+"_LifetimeAll", "8-bit black", PixX,PixY, 4096);
		}
		if(bIntLTImages)
		{
			for(i=0;i<4;i++)
				if(bDataPresent[i])
					impAverT[i]=IJ.createImage(shortFilename+"_C"+Integer.toString(i+1)+"_LifeTimePFrame_Bin="+Integer.toString(nTimeBin), "32-bit black", PixX,PixY, nTotalBins);
		}
				
		
		bBuff.position(dataPosition);

		int tempint=0;
		IJ.showStatus("Reading lifetime values...");
		
		for(int n=0;n<Records;n++){	
			byte[] record=new byte[4];
			bBuff.get(record,0,4);
			 recordData = ((record[3] & 0xFF) << 24) | ((record[2] & 0xFF) << 16) | ((record[1] & 0xFF) << 8) | (record[0] & 0xFF); //Convert from little endian

			 nsync= recordData&0xFFFF;
			dtime=(recordData>>>16)&0xFFF;
			 chan=(recordData>>>28)&0xF;
			curSync=ofltime+nsync;

			if (chan== 15){		
				if(dtime==0){
					ofltime+=WRAPAROUND;
				}
				else{
					markers =(recordData>>16)&0xF;

					if(markers==4) 
					{
						nCurrFrame+=1;
						//frameNb+= 1;
						frameStart=true;
						frameUpdate=true;
						curLine=0;
					}
					if (markers==1&&frameStart){
						insideLine=true;
						syncStart=curSync;
					}
					if (markers==2){
						insideLine=false;
						curLine++;						
					}
				}

			}else if (insideLine){
				curPixel=(int) Math.floor((curSync-syncStart)/(double)syncCountPerLine*PixX);
								
				if(bLTOrder)
				{
					impCh[chan-1].setSliceWithoutUpdate(dtime);
					datax=impCh[chan-1].getProcessor().getPixel(curPixel, curLine);
					datax++;
					impCh[chan-1].getProcessor().putPixel(curPixel, curLine, datax);
				}
				if(bIntLTImages)
				{
					//update frame to the next one
					if(frameUpdate)
					{
						nBinnedFrameN=(int)Math.ceil((double)nCurrFrame/(double)nTimeBin);
						impAverT[chan-1].setSliceWithoutUpdate(nBinnedFrameN);
						impInt[chan-1].setSliceWithoutUpdate(nBinnedFrameN);
						frameUpdate=false;
					}
					tempint = (int)Float.intBitsToFloat(impInt[chan-1].getProcessor().getPixel(curPixel, curLine));
					//non zero photon number
					if(tempint>0)
					{
						tempval=Float.intBitsToFloat(impAverT[chan-1].getProcessor().getPixel(curPixel, curLine));	
						tempval+=(float)dtime/(float)tempint;//calculate average
						impAverT[chan-1].getProcessor().putPixel(curPixel, curLine, Float.floatToIntBits(tempval));
					}
				}

			}
			IJ.showProgress(n+1, Records);

		}// END of read record loop///////////////////
		IJ.showProgress(Records, Records);
		IJ.showStatus("Reading lifetime values...done.");
		bBuff.clear(); // Clears the byte buffer containing the file data

		//set scale, add info and show images

		if(bLTOrder)
		{

			for(i=0;i<4;i++)
				if(bDataPresent[i])
				{
					impCh[i].setProperty("Info", AcquisitionInfo); 
					//image scale
					if(dPixSize>0)
					{
						cal = impCh[i].getCalibration();
						cal.setUnit("um");
						cal.pixelWidth=dPixSize;
						cal.pixelHeight=dPixSize;
						impCh[i].setCalibration(cal);
					}					

					impCh[i].show();
				}
		}
		
		if(bIntLTImages)
		{

			for(i=0;i<4;i++)
				if(bDataPresent[i])
				{
					impAverT[i].setProperty("Info", AcquisitionInfo);
					//image scale
					if(dPixSize>0)
					{
						cal = impAverT[i].getCalibration();
						cal.setUnit("um");
						cal.pixelWidth=dPixSize;
						cal.pixelHeight=dPixSize;
						impAverT[i].setCalibration(cal);
					}	
					impAverT[i].show();
				}
		}
		
		
		//	* /

	}
	
    public static int hex2dec(String s) {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }
        return val;
    }
    /** 
	 * Dialog displaying options for loading
	 * **/
	public boolean loadDialog(int nTotFrames) 
	{
		GenericDialog loadDialog = new GenericDialog("FLIM data load parameters");
		
		loadDialog.addCheckbox("Load Lifetime ordered stack", Prefs.get("PTU_Reader.bLTOrder", true));
		loadDialog.addCheckbox("Load Intensity and Lifetime Average stacks", Prefs.get("PTU_Reader.bIntLTImages", true));
		loadDialog.addMessage("Total number of frames: " + Integer.toString(nTotFrames) );
		loadDialog.addNumericField("Bin size in frames", Prefs.get("PTU_Reader.nTimeBin", 1), 0, 4, " frames");
		loadDialog.setResizable(false);
		loadDialog.showDialog();
		if (loadDialog.wasCanceled())
	        return false;
		
		bLTOrder = loadDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bLTOrder", bLTOrder);
		bIntLTImages = loadDialog.getNextBoolean();
		Prefs.set("PTU_Reader.bIntLTImages", bIntLTImages);		
		
		nTimeBin = (int)loadDialog.getNextNumber();
		if(nTimeBin<1 || nTimeBin>nTotFrames)
		{
			IJ.log("Bin size should be in the range from 1 to total frame size, resetting to 1");
			nTimeBin = 1;
		}
		Prefs.set("PTU_Reader.nTimeBin", nTimeBin);
		return true;
	}
	
	
	/** function  that reads from bBuff buffer header in the PTU format**/
	boolean readPTUHeader()
	{

		byte[] somebytes=new byte[8];
		bBuff.get(somebytes,0,8);
		String IdentString= new String(somebytes);
		//System.out.println("Ident: " + IdentString);
		IJ.log("Ident: " + IdentString);
		IdentString=IdentString.trim();

		if(!IdentString.equals("PQTTTR"))
		{
			IJ.log("Invalid, this is not an PTU file.");
			return false;
		}
		somebytes=new byte[8];
		bBuff.get(somebytes,0,8);
		String formatVersionStr=new String(somebytes);
		//System.out.println("Tag version: " + formatVersionStr);
		IJ.log("Tag version: " + formatVersionStr);
		
		String sTagIdent;
		int nTagIdx;
		int nTagTyp;
		
	    long nTagInt=0;
	    double nTagFloat=0.0;
	    String sEvalName;
	    String sTagString;
	    IJ.log("Reading header...");
		boolean bReadEnd = false;
		while(!bReadEnd)
	    {
			somebytes=new byte[32];
			bBuff.get(somebytes,0,32);
			sTagIdent = new String(somebytes);
			sTagIdent=sTagIdent.trim();
			//System.out.println(sTagIdent);
			somebytes=new byte[4];
			bBuff.get(somebytes,0,4);
			nTagIdx=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			somebytes=new byte[4];
			bBuff.get(somebytes,0,4);
			nTagTyp=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			if(nTagIdx>-1)
			{sEvalName =  sTagIdent+"("+Integer.toString(nTagIdx)+"):";}
			else
			{sEvalName =  sTagIdent+":";}
					
			switch(nTagTyp)
			{
			case tyEmpty8:
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				sEvalName =  sEvalName+"<Empty>";
				break;
			case tyBool8:
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				if(nTagInt==0)
					sEvalName =  sEvalName+"FALSE";
				else
					sEvalName =  sEvalName+"TRUE";
				break;
			case tyInt8:
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName =  sEvalName+Integer.toString((int)nTagInt);
				break;
			case tyBitSet64:
				//STUB _not_tested_
				System.out.println("tyBitSet64 field, not tested");
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName =  sEvalName+Integer.toString((int)nTagInt);
				break;
			case tyColor8:
				//STUB _not_tested_
				System.out.println("tyColor8 field, not tested");
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName =  sEvalName+Integer.toString((int)nTagInt);
				break;
			case tyFloat8:
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagFloat =ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
				sEvalName = sEvalName+Double.toString(nTagFloat);
				break;
			case tyTDateTime:
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				//nTagFloat =ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				nTagFloat =ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
				nTagInt = (long) (nTagFloat);
				nTagInt = (long) ((nTagFloat-719529+693960)*24*3600);//(add datenum(1899,12,30) minus linux tima)*in days -> to seconds
				Date itemDate = new Date(nTagInt*1000);
				sEvalName =sEvalName+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(itemDate);
				break;
			case tyFloat8Array:
				//STUB _not tested_
				System.out.println("tyFloat8Array field, not tested");
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName = sEvalName+"<Float array with "+Integer.toString((int)nTagInt/8)+" entries>";
				//just read them out
				somebytes=new byte[(int)nTagInt];
				bBuff.get(somebytes,0,(int)nTagInt);				
				
				break;
			case tyAnsiString:
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				somebytes=new byte[(int)nTagInt];
				bBuff.get(somebytes,0,(int)nTagInt);
				sTagString = new String(somebytes);
				sTagString=sTagString.trim();
				sEvalName =sEvalName+sTagString;
				break;
			case tyWideString:
				//STUB _not tested_
				System.out.println("tyWideString field, not tested");
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				somebytes=new byte[(int)nTagInt];
				bBuff.get(somebytes,0,(int)nTagInt);
				sTagString = new String(somebytes);
				sTagString=sTagString.trim();
				sEvalName =sEvalName+sTagString;
				//return;
				break;
			case tyBinaryBlob:
				System.out.println("tyBinaryBlob field, not tested");
				somebytes=new byte[8];
				bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName = sEvalName+"<Binary Blob with "+Integer.toString((int)nTagInt)+" bytes>";
				//just read them out
				somebytes=new byte[(int)nTagInt];
				bBuff.get(somebytes,0,(int)nTagInt);	
				//return;
				break;
				
			default:
					IJ.log("Oops, UNCATCHED field!");
			}	
			//log stuff
			IJ.log(sEvalName);
			stringInfo.append(sEvalName+"\n");
			if(sTagIdent.equals("Header_End"))
			{
				bReadEnd=true;
				IJ.log("Finished reading header.");
			}
			if(sTagIdent.equals("ImgHdr_PixX"))
			{
				PixX=(int)nTagInt;				
			}
			if(sTagIdent.equals("ImgHdr_PixY"))
			{
				PixY=(int)nTagInt;				
			}
			if(sTagIdent.equals("ImgHdr_PixResol"))
			{
				dPixSize=nTagFloat;				
			}
			if(sTagIdent.equals("TTResult_NumberOfRecords"))
			{
				Records=(int)nTagInt;
			}
			
			
	    }
		return true;
	}
	
	/** function  that reads from bBuff buffer header in the PT3 format**/
	boolean readPT3Header()
	{
		
		
		/* The following is binary file header information */

		int Curves;
		int BitsPerRecord;
		int RoutingChannels;
		int NumberOfBoards;
		int ActiveCurve;
		int MeasMode;
		int SubMode;
		int RangeNo;
		int Offset;
		int Tacq;				// in ms
		int StopAt;
		int StopOnOvfl;
		int Restart;
		int DispLinLog;
		int DispTimeFrom;		// 1ns steps
		int DispTimeTo;
		int RepeatMode;
		int RepeatsPerCurve;
		int RepeatTime;
		int RepeatWaitTime;

		/* The next is a board specific header */
		int HardwareSerial; 
		int SyncDivider;
		int CFDZeroCross0;
		int CFDLevel0;
		int CFDZeroCross1;
		int CFDLevel1;
		float Resolution;

		/* The next is a TTTR mode specific header */
		int ExtDevices;
		int Reserved1;
		int Reserved2;			
		int CntRate0;
		int CntRate1;
		int StopAfter;
		int StopReason;
		
		int ImgHdrSize;		

		byte[] somebytes=new byte[16];
		bBuff.get(somebytes,0,16);
		String IdentString= new String(somebytes);
		IJ.log("Ident: " + IdentString);
		stringInfo.append("Ident: " + IdentString+"\n");

		somebytes=new byte[6];
		bBuff.get(somebytes,0,6);
		String formatVersionStr=new String(somebytes);
		formatVersionStr=formatVersionStr.trim();
		
		IJ.log("format version: " + formatVersionStr);
		stringInfo.append("format version: " + formatVersionStr+"\n");
		if(!formatVersionStr.equals("2.0"))
		{
			IJ.log("Warning: This program is for version 2.0 only. Aborted.");
			return false;
		}
			
		somebytes=new byte[18];
		bBuff.get(somebytes,0,18);
		String CreatorNameStr=new String(somebytes);
		IJ.log("creator name: " + CreatorNameStr);
		stringInfo.append("creator name: " + CreatorNameStr+"\n");
		
		somebytes=new byte[12];
		bBuff.get(somebytes,0,12);
		String CreatorVersionStr=new String(somebytes);
		IJ.log("creator version: " + CreatorVersionStr);
		stringInfo.append("creator version: " + CreatorVersionStr+"\n");
		
		somebytes=new byte[18];
		bBuff.get(somebytes,0,18);
		String FileTimeStr=new String(somebytes);
		IJ.log("File time: " + FileTimeStr);
		stringInfo.append("File time: " + FileTimeStr+"\n");
		
		somebytes=new byte[2];
		bBuff.get(somebytes,0,2); // just to skip 

		somebytes=new byte[256];
		bBuff.get(somebytes,0,256);
		String CommentStr=new String(somebytes);
		IJ.log("Comment: " + CommentStr);
		stringInfo.append("Comment: " + CommentStr+"\n");

		//*******************************
		// Read T3 Header
		//*******************************

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Curves=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of curves: " + Curves);
		stringInfo.append("Nb of curves: " + Curves+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		BitsPerRecord=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("bits per record: " + BitsPerRecord);
		stringInfo.append("bits per record: " + BitsPerRecord+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		RoutingChannels=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of routing channels: " + RoutingChannels);
		stringInfo.append("Nb of routing channels: " + RoutingChannels+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		NumberOfBoards=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of boards: " + NumberOfBoards);
		stringInfo.append("Nb of boards: " + NumberOfBoards+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		ActiveCurve=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of active curve: " + ActiveCurve);
		stringInfo.append("Nb of active curve: " + ActiveCurve+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		MeasMode=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Measurement mode: " + MeasMode);
		stringInfo.append("Measurement mode: " + MeasMode+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		SubMode=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("SubMode: " + SubMode);
		stringInfo.append("SubMode: " + SubMode+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		RangeNo=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("RangeNo: " + RangeNo);
		stringInfo.append("RangeNo: " + RangeNo+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Offset=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Offset (ns): " + Offset);
		stringInfo.append("Offset (ns): " + Offset+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Tacq=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Acquisition time (ms): " + Tacq);
		stringInfo.append("Acquisition time (ms): " + Tacq+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		StopAt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("StopAt (counts): " + StopAt);
		stringInfo.append("StopAt (counts): " + StopAt+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		StopOnOvfl=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Stop On Overflow: " + StopOnOvfl);
		stringInfo.append("Stop On Overflow: " + StopOnOvfl+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Restart=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Restart: " + Restart);
		stringInfo.append("Restart: " + Restart+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		DispLinLog=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Display Lin/Log: " + DispLinLog);
		stringInfo.append("Display Lin/Log: " + DispLinLog+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		DispTimeFrom=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Display Time Axis From (ns): " + DispTimeFrom);
		stringInfo.append("Display Time Axis From (ns): " + DispTimeFrom+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		DispTimeTo=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Display Time Axit To (ns): " + DispTimeTo);
		stringInfo.append("Display Time Axit To (ns): " + DispTimeTo+"\n");

		somebytes=new byte[108];
		bBuff.get(somebytes,0,108); // Skipping display parameters

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		RepeatMode=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Repeat Mode: " + RepeatMode);
		stringInfo.append("Repeat Mode: " + RepeatMode+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		RepeatsPerCurve=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Repeats Per Curve: " + RepeatsPerCurve);
		stringInfo.append("Repeats Per Curve: " + RepeatsPerCurve+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		RepeatTime=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("RepeatTime: " + RepeatTime);
		stringInfo.append("RepeatTime: " + RepeatTime+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		RepeatWaitTime=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("RepeatWaitTime: " + RepeatWaitTime);
		stringInfo.append("RepeatWaitTime: " + RepeatWaitTime+"\n");

		somebytes=new byte[20];
		bBuff.get(somebytes,0,20);
		String ScriptNameStr=new String(somebytes);
		IJ.log("ScriptName: " + ScriptNameStr);
		stringInfo.append("ScriptName: " + ScriptNameStr+"\n");


		//*******************************
		// Read Board Header
		//*******************************

		somebytes=new byte[16];
		bBuff.get(somebytes,0,16);
		String HardwareStr=new String(somebytes);
		IJ.log("Hardware Identifier: " + HardwareStr);
		stringInfo.append("Hardware Identifier: " + HardwareStr+"\n");
		
		somebytes=new byte[8];
		bBuff.get(somebytes,0,8);
		String HardwareVer=new String(somebytes);
		IJ.log("Hardware Version: " + HardwareVer);
		stringInfo.append("Hardware Version: " + HardwareVer+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		HardwareSerial=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("HardwareSerial: " + HardwareSerial);
		stringInfo.append("HardwareSerial: " + HardwareSerial+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		SyncDivider=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("SyncDivider: " + SyncDivider);
		stringInfo.append("SyncDivider: " + SyncDivider+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		CFDZeroCross0=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFDZeroCross (Ch0), (mV): " + CFDZeroCross0);
		stringInfo.append("CFDZeroCross (Ch0), (mV): " + CFDZeroCross0+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		CFDLevel0=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFD Discr (Ch0), (mV): " + CFDLevel0);
		stringInfo.append("CFD Discr (Ch0), (mV): " + CFDLevel0+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		CFDZeroCross1=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFD ZeroCross (Ch1), (mV): " + CFDZeroCross1);
		stringInfo.append("CFD ZeroCross1 (Ch0), (mV): " + CFDZeroCross1+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		CFDLevel1=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFD Discr (Ch1), (mV): " + CFDLevel1);
		stringInfo.append("CFD Discr (Ch1), (mV): " + CFDLevel1+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Resolution = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		IJ.log("Resolution (ns): " + Resolution);
		stringInfo.append("Resolution (ns): " + Resolution+"\n");

		somebytes=new byte[104];
		bBuff.get(somebytes,0,104); // Skip router settings

		//*******************************
		// Read Specific T3 Header
		//*******************************

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		ExtDevices=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("ExtDevices: " + ExtDevices);
		stringInfo.append("ExtDevices: " + ExtDevices+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Reserved1=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Reserved1: " + Reserved1);
		stringInfo.append("Reserved1: " + Reserved1+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Reserved2=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Reserved2: " + Reserved2);
		stringInfo.append("Reserved2: " + Reserved2+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		CntRate0=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Count Rate (Ch0) (Hz): " + CntRate0);
		stringInfo.append("Count Rate (Ch0) (Hz): " + CntRate0+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		CntRate1=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Count Rate (Ch1) (Hz): " + CntRate1);
		stringInfo.append("Count Rate (Ch1) (Hz): " + CntRate1+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		StopAfter=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Stop After (ms): " + StopAfter);
		stringInfo.append("StopAfter (ms): " + StopAfter+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		StopReason=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("StopReason: " + StopReason);
		stringInfo.append("StopReason: " + StopReason+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		Records=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Records: " + Records);
		stringInfo.append("Records: " + Records+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		ImgHdrSize=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Imaging Header Size (bytes): " + ImgHdrSize);
		stringInfo.append("Imaging Header Size (bytes): " + ImgHdrSize+"\n");
		
		if(ImgHdrSize==0)
		{
			IJ.error("Not a FLIM image file!");
			return false;
		}
		//*******************************
		// Read Imaging Header
		//*******************************
		//	somebytes=new byte[ImgHdrSize*4];
		//	bBuff.get(somebytes,0,ImgHdrSize*4); // Skipping the Imaging header

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		int Dimensions=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Dimensions: " + Dimensions);
		stringInfo.append("Dimensions: " + Dimensions+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		int IdentImg=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("IdentImg: " + IdentImg);
		stringInfo.append("IdentImg: " + IdentImg+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		int Frame=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Frame: " + Frame);
		stringInfo.append("Frame: " + Frame+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		int LineStart=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("LineStart: " + LineStart);
		stringInfo.append("LineStart: " + LineStart+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		int LineStop=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("LineStop: " + LineStop);
		stringInfo.append("LineStop: " + LineStop+"\n");

		somebytes=new byte[1];
		bBuff.get(somebytes,0,1);
		int Pattern=somebytes[0];
		IJ.log("Pattern: " + Pattern);
		stringInfo.append("Pattern: " + Pattern+"\n");

		somebytes=new byte[3];
		bBuff.get(somebytes,0,3); //Skipping TCPIP Protocol parameters

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		PixX=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Image width (px): " + PixX);
		stringInfo.append("Image width (px): " + PixX+"\n");

		somebytes=new byte[4];
		bBuff.get(somebytes,0,4);
		PixY=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Image height (px): " + PixY);
		stringInfo.append("Image height (px): " + PixY+"\n");

		somebytes=new byte[(ImgHdrSize-8)*4];
		bBuff.get(somebytes,0,(ImgHdrSize-8)*4); //Skipping TCPIP Protocol parameters

		return true;
	
	}
	

}