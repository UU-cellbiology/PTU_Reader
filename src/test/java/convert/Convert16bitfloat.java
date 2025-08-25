package convert;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

public class Convert16bitfloat
{
	public static void main( final String[] args )
	{
		new ImageJ();
		IJ.open( "/home/eugene/Desktop/projects/PTU_reader/20250825_example_Leica_FLIM/FastFlim_LIF.tif" );
		ImagePlus imp = IJ.getImage();
		ImageProcessor ip = imp.getProcessor();
		int nW = imp.getWidth();
		int nH = imp.getHeight();
		ImagePlus out = IJ.createImage("converted", "32-bit black", nW, nH, 1);
		ImageProcessor ipout = out.getProcessor();
		for(int i = 0; i<nW; i++)
			for(int j = 0; j<nH; j++)
			{
				int val = ip.get( i, j );
				ipout.setf( i, j, toFloat(val) );
			}
		
		System.out.println(ip.get( 10, 110 ));
		out.show();
	}
	
	public static float toFloat( int hbits )
	{
	    int mant = hbits & 0x03ff;            // 10 bits mantissa
	    int exp =  hbits & 0x7c00;            // 5 bits exponent
	    if( exp == 0x7c00 )                   // NaN/Inf
	        exp = 0x3fc00;                    // -> NaN/Inf
	    else if( exp != 0 )                   // normalized value
	    {
	        exp += 0x1c000;                   // exp - 15 + 127
	        if( mant == 0 && exp > 0x1c400 )  // smooth transition
	            return Float.intBitsToFloat( ( hbits & 0x8000 ) << 16
	                                            | exp << 13 | 0x3ff );
	    }
	    else if( mant != 0 )                  // && exp==0 -> subnormal
	    {
	        exp = 0x1c400;                    // make it normal
	        do {
	            mant <<= 1;                   // mantissa * 2
	            exp -= 0x400;                 // decrease exp by 1
	        } while( ( mant & 0x400 ) == 0 ); // while not normal
	        mant &= 0x3ff;                    // discard subnormal bit
	    }                                     // else +/-0 -> +/-0
	    return Float.intBitsToFloat(          // combine all parts
	        ( hbits & 0x8000 ) << 16          // sign  << ( 31 - 15 )
	        | ( exp | mant ) << 13 );         // value << ( 23 - 10 )
	}
}
