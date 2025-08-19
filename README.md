# PTU_Reader
[ImageJ](https://imagej.nih.gov/ij/)/[FIJI](http://fiji.sc/) plugin reading PicoQuant ptu/pt3 FLIM TTTR image files.

It is based/upgraded from Pt3Reader plugin developed by François Waharte and [Matlab code](https://github.com/PicoQuant/PicoQuant-Time-Tagged-File-Format-Demos) from PicoQuant.  
![PTU_Reader logo](http://katpyxa.info/software/PTU_Reader_logo.png "logo")

## How to install plugin

1. You need to download and install [ImageJ](https://imagej.nih.gov/ij/download.html) or [FIJI](http://fiji.sc/#download) on your computer first.
2. [Download *PTU_Reader_...jar*](https://github.com/UU-cellbiology/PTU_Reader/releases) from the latest release and place it in the "plugins" folder of ImageJ/FIJI.
3. Plugin will appear as *PTU_Reader* in ImageJ's *Plugins* menu.

## How to run plugin

1. Click *PTU_Reader* line in ImageJ's *Plugins* menu.
2. In "Open File" dialog choose file with (ptu/pt3) extension to load.
3. Plugin will read file's header and provide you with following options:  
![Menu](http://katpyxa.info/software/PTU_Reader/Menu2.png "Menu")
4. Choose what images/stacks you want to load (see detailed description below) and click *OK*.

## Output #1: lifetime ordered stack

This is 8-bit stack containing NFRAMES frames. Each frame corresponds to a specific "lifetime count". To convert "lifetime count" (or z-stack position) to the real time value in nanoseconds (ns), you need to multiply it by the value of *MeasDesc_Resolution* parameter (provided in seconds). It is generated in the ImageJ Log window during PTU file loading.
For example, *MeasDesc_Resolution* value of 9.696969697E-11 seconds is approximately equal to 97 picoseconds or 0.097 nanoseconds.
This means that on z-slice 1 we have photons with detected lifetime of zero, at z-slice 2 photons with detected lifetime of 97 ps, z-slice 3 is 97*2=194 ns, etc.

The intensity of pixel at *x*,*y* position corresponds to the number of photons with this lifetime during the *whole acquisition*.  
Don't forget to do *Image->Adjust->Brightness/Contrast* to see the signal.

For example, to get a FLIM decay curve of some image area that would look like this:  
![Curve example](http://katpyxa.info/software/PTU_Reader/Curve_example.png "curve")  

select rectangular ROI and go to *Image->Stacks->Plot Z-axis profile*. You can make plot *y*-axis logarithmic by clicking *More>>Set Range..*

In addition, there are two loading options: "*Load whole stack*" and "*Load binned*". First option assembles all photons in one stack with NFRAMES frames (lifetimes). "Load binned" creates Hyperstack where z coordinate corresponds to "counts of lifetime", while "*time*" corresponds to binned frame intervals specified by "*Bin size in frames*" parameter below. Last option can generate HUGE files (since imagewidth x imageheight x NFRAMES x binned frames), so be aware about it.

If you choose "*Load only frame range*" option at the bottom of the dialog, it will also restrict detected photons to the specified range.

## Output #2: Intensity and Lifetime Average stacks
Lifetime acquisition often happens over multiple frames. If this option is checked in the menu, plugin will provide intensity stack for each frame (if binning is 1) or summarized intensity of multiple frames for the bin size larger than 1.  

In addition, for the same number of binned frames, it will generate average lifetime value map.  
**Important**: the average lifetime value is presented in the same "counts of lifetime"! If you want it to be presented in the physical units, you need to multiply all intensity values by *MeasDesc_Resolution* parameters (in s, ns or ps). For the whole image/stack it can be done using ImageJ *Process->Math->Multiply..* function.

So you can observe average lifetime change during acquisition. It is recommended to you different LUT to highlight its changes (*Image->Lookup Tables->Spectrum* or *Rainbow RGB*)

These two stacks are in 32-bit format.

You can restrict the interval of loaded data by selecting "*Load only frame range*" checkbox and providing the range of frames to load.

## It can not read my files! What about pt2? There is error!
Send me example of your file by email, describe the problem and I'll try to incorporate it to the plugin.

## Updates history
v.0.1.0 (2024.13) (breaking) Fixed loading of the first line error. Performed mavenization of the whole project. 

v.0.0.9 (2020.11) Thanks to Robert Hauschild feedback, corrected PT3 file format reading issues (actually it was diabled before). Added version number to the plugin menu, next to its name.

v.0.0.8 (2020.09) Thanks to Emma Wilson feedback, corrected some HydraHarp/TimeHarp260 T3 file format issues. Corrected channel names in the exported stack.

v.0.0.7 (2019.02) Thanks to Marco Dalla Vecchia feedback, now HydraHarp/TimeHarp260 T3 file format is supported. Plus plugin works correctly with multi-channel FLIM data. The error of dtime=0 is fixed. Added progress bar for initial data assessment.

v.0.0.6 (2018.03) Thanks to Tanja Kaufmann feedback, data reading is updated. Now there are two modes of reading, depending if the Frame marker is present. Plus LineStart and LineStop marker values are read from the header. + WRAPAROUND value is changed to 65536.

v.0.0.5 (2017.05) Thanks to Shunsuke Takeda feedback, the error of "missing first frame" is eliminated.  

v.0.0.4 (2017.04) Thanks to Bruno Scocozza feedback, frame marker bug during loading is fixed now. Plus, "frame range" and lifetime binning options are added.  

v.0.0.2 (2017.03) file dialog changed to system default (now works on Mac, no need in java library).

---
Developed in [Cell Biology group](http://cellbiology.science.uu.nl/) of Utrecht University.  
Email katpyxa @ gmail.com for any questions/comments/suggestions.
