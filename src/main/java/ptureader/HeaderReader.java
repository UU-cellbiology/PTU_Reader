package ptureader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;

import ij.IJ;

public class HeaderReader
{
	
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
    
    // RecordTypes
    final static int rtPicoHarpT3     = 66307;   //hex2dec('00010303');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $03 (T3), HW: $03 (PicoHarp)
    final static int rtPicoHarpT2     = 66051;   //hex2dec('00010203');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $02 (T2), HW: $03 (PicoHarp)
    final static int rtHydraHarpT3    = 66308;   //hex2dec('00010304');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $03 (T3), HW: $04 (HydraHarp)
    final static int rtHydraHarpT2    = 66052;   //hex2dec('00010204');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $02 (T2), HW: $04 (HydraHarp)
    final static int rtHydraHarp2T3   = 16843524;//hex2dec('01010304');% (SubID = $01 ,RecFmt: $01) (V2), T-Mode: $03 (T3), HW: $04 (HydraHarp)
    final static int rtHydraHarp2T2   = 16843268;//hex2dec('01010204');% (SubID = $01 ,RecFmt: $01) (V2), T-Mode: $02 (T2), HW: $04 (HydraHarp)
    final static int rtTimeHarp260NT3 = 66309;   //hex2dec('00010305');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $03 (T3), HW: $05 (TimeHarp260N)
    final static int rtTimeHarp260NT2 = 66053;   //hex2dec('00010205');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $02 (T2), HW: $05 (TimeHarp260N)
    final static int rtTimeHarp260PT3 = 66310;   //hex2dec('00010306');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $03 (T3), HW: $06 (TimeHarp260P)
    final static int rtTimeHarp260PT2 = 66054;   //hex2dec('00010206');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $02 (T2), HW: $06 (TimeHarp260P)
    final static int rtMultiHarpNT3   = 66311;   //hex2dec('00010307');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $03 (T3), HW: $07 (MultiHarp150N)
    final static int rtMultiHarpNT2   = 66055;   //hex2dec('00010207');% (SubID = $00 ,RecFmt: $01) (V1), T-Mode: $02 (T2), HW: $07 (MultiHarp150N)

    
	/** function  that reads from bBuff buffer header in the PTU format**/
	public static boolean readPTUHeader(final PTU_Reader_ ptu)
	{

		byte[] somebytes=new byte[8];
		ptu.bBuff.get(somebytes,0,8);
		String IdentString= new String(somebytes);
		//System.out.println("Ident: " + IdentString);
		IJ.log("Ident: " + IdentString);
		IdentString=IdentString.trim();

		if(!IdentString.equals("PQTTTR"))
		{
			IJ.log("Invalid, this is not an PTU file.");
			return false;
		}
		somebytes = new byte[8];
		ptu.bBuff.get(somebytes,0,8);
		String formatVersionStr = new String(somebytes);
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
			ptu.bBuff.get(somebytes,0,32);
			sTagIdent = new String(somebytes);
			sTagIdent = sTagIdent.trim();
			//System.out.println(sTagIdent);
			
			somebytes=new byte[4];
			ptu.bBuff.get(somebytes,0,4);
			nTagIdx = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			
			somebytes = new byte[4];
			ptu.bBuff.get(somebytes,0,4);
			nTagTyp=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			
			if(nTagIdx>-1)
			{sEvalName =  sTagIdent+"("+Integer.toString(nTagIdx)+"):";}
			else
			{sEvalName =  sTagIdent+":";}
					
			switch(nTagTyp)
			{
			case tyEmpty8:
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				sEvalName =  sEvalName+"<Empty>";
				break;
			case tyBool8:
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				if(nTagInt==0)
					sEvalName =  sEvalName+"FALSE";
				else
					sEvalName =  sEvalName+"TRUE";
				break;
			case tyInt8:
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName =  sEvalName+Integer.toString((int)nTagInt);
				break;
			case tyBitSet64:
				//STUB _not_tested_
				System.out.println("tyBitSet64 field, not tested");
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName =  sEvalName+Integer.toString((int)nTagInt);
				break;
			case tyColor8:
				//STUB _not_tested_
				System.out.println("tyColor8 field, not tested");
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName =  sEvalName+Integer.toString((int)nTagInt);
				break;
			case tyFloat8:
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagFloat =ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
				sEvalName = sEvalName+Double.toString(nTagFloat);
				break;
			case tyTDateTime:
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
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
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName = sEvalName+"<Float array with "+Integer.toString((int)nTagInt/8)+" entries>";
				//just read them out
				somebytes=new byte[(int)nTagInt];
				ptu.bBuff.get(somebytes,0,(int)nTagInt);				
				
				break;
			case tyAnsiString:
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				somebytes=new byte[(int)nTagInt];
				ptu.bBuff.get(somebytes,0,(int)nTagInt);
				sTagString = new String(somebytes);
				sTagString=sTagString.trim();
				sEvalName =sEvalName+sTagString;
				break;
			case tyWideString:
				//STUB _not tested_
				System.out.println("tyWideString field, not tested");
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				somebytes=new byte[(int)nTagInt];
				ptu.bBuff.get(somebytes,0,(int)nTagInt);
				sTagString = new String(somebytes);
				sTagString=sTagString.trim();
				sEvalName =sEvalName+sTagString;
				//return;
				break;
			case tyBinaryBlob:
				System.out.println("tyBinaryBlob field, not tested");
				somebytes=new byte[8];
				ptu.bBuff.get(somebytes,0,8);
				nTagInt=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				sEvalName = sEvalName+"<Binary Blob with "+Integer.toString((int)nTagInt)+" bytes>";
				//just read them out
				somebytes = new byte[(int)nTagInt];
				ptu.bBuff.get(somebytes,0,(int)nTagInt);	
				//return;
				break;
				
			default:
					IJ.log("Oops, UNCATCHED field!");
			}	
			//log stuff
			IJ.log(sEvalName);
			ptu.stringInfo.append(sEvalName+"\n");
			if(sTagIdent.equals("Header_End"))
			{
				bReadEnd=true;
				IJ.log("Finished reading header.");
			}
			if(sTagIdent.equals("ImgHdr_PixX"))
			{
				ptu.nPixX = (int)nTagInt;				
			}
			if(sTagIdent.equals("ImgHdr_PixY"))
			{
				ptu.nPixY = (int)nTagInt;				
			}
			if(sTagIdent.equals("ImgHdr_PixResol"))
			{
				ptu.dPixSize = nTagFloat;				
			}
			if(sTagIdent.equals("MeasDesc_Resolution"))
			{
				ptu.fTimeResolution = ( float ) nTagFloat*1000000000.0f;				
			}			
			
			if(sTagIdent.equals("TTResult_NumberOfRecords"))
			{
				ptu.nRecords = (int)nTagInt;
			}
			if(sTagIdent.equals("ImgHdr_LineStart"))
			{
				ptu.nLineStart = (int)nTagInt;				
			}
			if(sTagIdent.equals("ImgHdr_LineStop"))
			{
				ptu.nLineStop = (int)nTagInt;				
			}
			if(sTagIdent.equals("ImgHdr_Frame"))
			{
				ptu.nFrameMark = (int)nTagInt;				
			}	
			if(sTagIdent.equals("TTResultFormat_TTTRRecType"))
			{
				switch ((int)nTagInt)
				{
				case rtPicoHarpT3:
					ptu.isT2 = false;
					IJ.log("PicoHarp T3 data");
					break;
				 case rtPicoHarpT2:
		            ptu.isT2  = true;
		            IJ.log("PicoHarp T2 data");
		            break;
		        case rtHydraHarpT3:
		            ptu.isT2  = false;
		            IJ.log("HydraHarp V1 T3 data");
		            break;
		        case rtHydraHarpT2:
		            ptu.isT2  = true;
		            IJ.log("HydraHarp V1 T2 data");
		            break;
		        case rtHydraHarp2T3:
		            ptu.isT2 = false;
		            IJ.log("HydraHarp V2 T3 data");
		            break;
		        case rtHydraHarp2T2:
		            ptu.isT2 = true;
		            IJ.log("HydraHarp V2 T2 data");
		            break;
		        case rtTimeHarp260NT3:
		            ptu.isT2 =  false;
		            IJ.log("TimeHarp260N T3 data");
		            break;
		        case rtTimeHarp260NT2:
		            ptu.isT2 = true;
		            IJ.log("TimeHarp260N T2 data");
		            break;
		        case rtTimeHarp260PT3:
		            ptu.isT2 = false;
		            IJ.log("TimeHarp260P T3 data");
		            break;
		        case rtTimeHarp260PT2:
		            ptu.isT2 = true;
		            IJ.log("TimeHarp260P T2 data");
		            break;
		        case rtMultiHarpNT3:
		            ptu.isT2 = false;
		            IJ.log("MultiHarp150N T3 data");
		            break;
		        case rtMultiHarpNT2:
		            ptu.isT2 = true;
		            IJ.log("MultiHarp150N T2 data");
		            break;
		        default:
		        	IJ.error("Invalid Record Type!");
		        	return false;
				}
				ptu.nRecordType = (int)nTagInt;
				if(ptu.nRecordType==rtPicoHarpT3 || ptu.nRecordType==rtHydraHarp2T3 || ptu.nRecordType==rtMultiHarpNT3|| ptu.nRecordType==rtHydraHarp2T3|| ptu.nRecordType==rtTimeHarp260NT3 || ptu.nRecordType==rtTimeHarp260PT3)
				{
					if(ptu.nRecordType==rtHydraHarpT3)
						ptu.nHT3Version = 1;
					else
						ptu.nHT3Version = 2;
				}
				else
				{
					IJ.error("So far in v." + ptu.sVersion + " only PicoHarp and HydraHarp are supported (and your file has different record type).\n Send example of PTU file to katpyxa@gmail.com");
		        	return false;
				}
			}
			
			
	    }
		return true;
	}
	
	/** function  that reads from bBuff buffer header in the PT3 format**/
	final static boolean readPT3Header(final PTU_Reader_ ptu)
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
		ptu.bBuff.get(somebytes,0,16);
		String IdentString= new String(somebytes);
		IJ.log("Ident: " + IdentString);
		ptu.stringInfo.append("Ident: " + IdentString+"\n");

		somebytes=new byte[6];
		ptu.bBuff.get(somebytes,0,6);
		String formatVersionStr=new String(somebytes);
		formatVersionStr=formatVersionStr.trim();
		
		IJ.log("format version: " + formatVersionStr);
		ptu.stringInfo.append("format version: " + formatVersionStr+"\n");
		if(!formatVersionStr.equals("2.0"))
		{
			IJ.log("Warning: This program is for version 2.0 only. Aborted.");
			return false;
		}
			
		somebytes = new byte[18];
		ptu.bBuff.get(somebytes,0,18);
		String CreatorNameStr=new String(somebytes);
		IJ.log("creator name: " + CreatorNameStr);
		ptu.stringInfo.append("creator name: " + CreatorNameStr+"\n");
		
		somebytes = new byte[12];
		ptu.bBuff.get(somebytes,0,12);
		String CreatorVersionStr = new String(somebytes);
		IJ.log("creator version: " + CreatorVersionStr);
		ptu.stringInfo.append("creator version: " + CreatorVersionStr+"\n");
		
		somebytes = new byte[18];
		ptu.bBuff.get(somebytes,0,18);
		String FileTimeStr = new String(somebytes);
		IJ.log("File time: " + FileTimeStr);
		ptu.stringInfo.append("File time: " + FileTimeStr+"\n");
		
		somebytes = new byte[2];
		ptu.bBuff.get(somebytes,0,2); // just to skip 

		somebytes=new byte[256];
		ptu.bBuff.get(somebytes,0,256);
		String CommentStr = new String(somebytes);
		IJ.log("Comment: " + CommentStr);
		ptu.stringInfo.append("Comment: " + CommentStr+"\n");

		//*******************************
		// Read T3 Header
		//*******************************

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Curves = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of curves: " + Curves);
		ptu.stringInfo.append("Nb of curves: " + Curves+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		BitsPerRecord = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("bits per record: " + BitsPerRecord);
		ptu.stringInfo.append("bits per record: " + BitsPerRecord+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		RoutingChannels = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of routing channels: " + RoutingChannels);
		ptu.stringInfo.append("Nb of routing channels: " + RoutingChannels+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		NumberOfBoards = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of boards: " + NumberOfBoards);
		ptu.stringInfo.append("Nb of boards: " + NumberOfBoards+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ActiveCurve = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Nb of active curve: " + ActiveCurve);
		ptu.stringInfo.append("Nb of active curve: " + ActiveCurve+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		MeasMode = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Measurement mode: " + MeasMode);
		ptu.stringInfo.append("Measurement mode: " + MeasMode+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		SubMode = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("SubMode: " + SubMode);
		ptu.stringInfo.append("SubMode: " + SubMode+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		RangeNo = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("RangeNo: " + RangeNo);
		ptu.stringInfo.append("RangeNo: " + RangeNo+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Offset = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Offset (ns): " + Offset);
		ptu.stringInfo.append("Offset (ns): " + Offset+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Tacq = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Acquisition time (ms): " + Tacq);
		ptu.stringInfo.append("Acquisition time (ms): " + Tacq+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		StopAt = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("StopAt (counts): " + StopAt);
		ptu.stringInfo.append("StopAt (counts): " + StopAt+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		StopOnOvfl = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Stop On Overflow: " + StopOnOvfl);
		ptu.stringInfo.append("Stop On Overflow: " + StopOnOvfl+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Restart = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Restart: " + Restart);
		ptu.stringInfo.append("Restart: " + Restart+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		DispLinLog = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Display Lin/Log: " + DispLinLog);
		ptu.stringInfo.append("Display Lin/Log: " + DispLinLog+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		DispTimeFrom = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Display Time Axis From (ns): " + DispTimeFrom);
		ptu.stringInfo.append("Display Time Axis From (ns): " + DispTimeFrom+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		DispTimeTo = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Display Time Axit To (ns): " + DispTimeTo);
		ptu.stringInfo.append("Display Time Axit To (ns): " + DispTimeTo+"\n");

		somebytes = new byte[108];
		ptu.bBuff.get(somebytes,0,108); // Skipping display parameters

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		RepeatMode = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Repeat Mode: " + RepeatMode);
		ptu.stringInfo.append("Repeat Mode: " + RepeatMode+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		RepeatsPerCurve = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Repeats Per Curve: " + RepeatsPerCurve);
		ptu.stringInfo.append("Repeats Per Curve: " + RepeatsPerCurve+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		RepeatTime = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("RepeatTime: " + RepeatTime);
		ptu.stringInfo.append("RepeatTime: " + RepeatTime+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		RepeatWaitTime = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("RepeatWaitTime: " + RepeatWaitTime);
		ptu.stringInfo.append("RepeatWaitTime: " + RepeatWaitTime+"\n");

		somebytes = new byte[20];
		ptu.bBuff.get(somebytes,0,20);
		String ScriptNameStr = new String(somebytes);
		IJ.log("ScriptName: " + ScriptNameStr);
		ptu.stringInfo.append("ScriptName: " + ScriptNameStr+"\n");


		//*******************************
		// Read Board Header
		//*******************************

		somebytes = new byte[16];
		ptu.bBuff.get(somebytes,0,16);
		String HardwareStr = new String(somebytes);
		IJ.log("Hardware Identifier: " + HardwareStr);
		ptu.stringInfo.append("Hardware Identifier: " + HardwareStr+"\n");
		
		somebytes = new byte[8];
		ptu.bBuff.get(somebytes,0,8);
		String HardwareVer = new String(somebytes);
		IJ.log("Hardware Version: " + HardwareVer);
		ptu.stringInfo.append("Hardware Version: " + HardwareVer+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		HardwareSerial = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("HardwareSerial: " + HardwareSerial);
		ptu.stringInfo.append("HardwareSerial: " + HardwareSerial+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		SyncDivider = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("SyncDivider: " + SyncDivider);
		ptu.stringInfo.append("SyncDivider: " + SyncDivider+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		CFDZeroCross0 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFDZeroCross (Ch0), (mV): " + CFDZeroCross0);
		ptu.stringInfo.append("CFDZeroCross (Ch0), (mV): " + CFDZeroCross0+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		CFDLevel0=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFD Discr (Ch0), (mV): " + CFDLevel0);
		ptu.stringInfo.append("CFD Discr (Ch0), (mV): " + CFDLevel0+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		CFDZeroCross1 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFD ZeroCross (Ch1), (mV): " + CFDZeroCross1);
		ptu.stringInfo.append("CFD ZeroCross1 (Ch0), (mV): " + CFDZeroCross1+"\n");

		somebytes=new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		CFDLevel1 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("CFD Discr (Ch1), (mV): " + CFDLevel1);
		ptu.stringInfo.append("CFD Discr (Ch1), (mV): " + CFDLevel1+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Resolution = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		IJ.log("Resolution (ns): " + Resolution);
		ptu.stringInfo.append("Resolution (ns): " + Resolution+"\n");
		ptu.fTimeResolution = Resolution;			
		
		somebytes = new byte[104];
		ptu.bBuff.get(somebytes,0,104); // Skip router settings

		//*******************************
		// Read Specific T3 Header
		//*******************************

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ExtDevices = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("ExtDevices: " + ExtDevices);
		ptu.stringInfo.append("ExtDevices: " + ExtDevices+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Reserved1 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Reserved1: " + Reserved1);
		ptu.stringInfo.append("Reserved1: " + Reserved1+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		Reserved2 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Reserved2: " + Reserved2);
		ptu.stringInfo.append("Reserved2: " + Reserved2+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		CntRate0 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Count Rate (Ch0) (Hz): " + CntRate0);
		ptu.stringInfo.append("Count Rate (Ch0) (Hz): " + CntRate0+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		CntRate1 = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Count Rate (Ch1) (Hz): " + CntRate1);
		ptu.stringInfo.append("Count Rate (Ch1) (Hz): " + CntRate1+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		StopAfter = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Stop After (ms): " + StopAfter);
		ptu.stringInfo.append("StopAfter (ms): " + StopAfter+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		StopReason=ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("StopReason: " + StopReason);
		ptu.stringInfo.append("StopReason: " + StopReason+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ptu.nRecords = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Records: " + ptu.nRecords);
		ptu.stringInfo.append("Records: " + ptu.nRecords+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ImgHdrSize = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Imaging Header Size (bytes): " + ImgHdrSize);
		ptu.stringInfo.append("Imaging Header Size (bytes): " + ImgHdrSize+"\n");
		
		if(ImgHdrSize == 0)
		{
			IJ.error("Not a FLIM image file!");
			return false;
		}
		//*******************************
		// Read Imaging Header
		//*******************************
		//	somebytes=new byte[ImgHdrSize*4];
		//	bBuff.get(somebytes,0,ImgHdrSize*4); // Skipping the Imaging header

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		int Dimensions = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Dimensions: " + Dimensions);
		ptu.stringInfo.append("Dimensions: " + Dimensions+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		int IdentImg = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("IdentImg: " + IdentImg);
		ptu.stringInfo.append("IdentImg: " + IdentImg+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ptu.nFrameMark = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Frame mark: " + ptu.nFrameMark);
		ptu.stringInfo.append("Frame: " + ptu.nFrameMark+"\n");

		somebytes=new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ptu.nLineStart = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("LineStart: " + ptu.nLineStart);
		ptu.stringInfo.append("LineStart: " + ptu.nLineStart+"\n");

		somebytes=new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ptu.nLineStop = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("LineStop: " + ptu.nLineStop);
		ptu.stringInfo.append("LineStop: " + ptu.nLineStop+"\n");

		somebytes = new byte[1];
		ptu.bBuff.get(somebytes,0,1);
		int Pattern = somebytes[0];
		IJ.log("Pattern: " + Pattern);
		ptu.stringInfo.append("Pattern: " + Pattern+"\n");

		somebytes = new byte[3];
		ptu.bBuff.get(somebytes,0,3); //Skipping TCPIP Protocol parameters

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ptu.nPixX = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Image width (px): " + ptu.nPixX);
		ptu.stringInfo.append("Image width (px): " + ptu.nPixX+"\n");

		somebytes = new byte[4];
		ptu.bBuff.get(somebytes,0,4);
		ptu.nPixY = ByteBuffer.wrap(somebytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
		IJ.log("Image height (px): " + ptu.nPixY);
		ptu.stringInfo.append("Image height (px): " + ptu.nPixY+"\n");

		somebytes = new byte[(ImgHdrSize-8)*4];
		ptu.bBuff.get(somebytes,0,(ImgHdrSize-8)*4); //Skipping TCPIP Protocol parameters

		return true;
	
	}
}
