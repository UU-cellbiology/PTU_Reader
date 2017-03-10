# PTU_Reader
[ImageJ](https://imagej.nih.gov/ij/)/[FIJI](http://fiji.sc/) plugin reading PicoQuant ptu/pt3 FLIM TTTR image files.

It is based/upgraded from [Pt3Reader](http://imagejdocu.tudor.lu/doku.php?id=plugin:inputoutput:picoquant_.pt3_image_reader:start) plugin developed by François Waharte and [Matlab code](https://github.com/PicoQuant/PicoQuant-Time-Tagged-File-Format-Demos/blob/master/PTU/Matlab/Read_PTU.m) from PicoQuant.  
![PTU_Reader logo](http://katpyxa.info/software/PTU_Reader_logo.png "logo") 

## How to install plugin

1. You need to download and install [ImageJ](https://imagej.nih.gov/ij/download.html) or [FIJI](http://fiji.sc/#download) on your computer first.
2. [Download](https://github.com/ekatrukha/PTU_Reader/raw/master/PTU_Reader_0.0.1_.jar) PTU_Reader_.jar and place it in the "plugins" folder of ImageJ/FIJI.
3. Plugin will appear as *PTU_Reader* in ImageJ's *Plugins* menu.

## How to run plugin

1. Click *PTU_Reader* line in ImageJ's *Plugins* menu.
2. In "Open File" dialog choose file extension (ptu/pt3) and file to load.
![dropdown](http://katpyxa.info/software/PTU_Reader/Dropdown.png "filemenu") 
3. Plugin will read file's header and provide you with following options:  
![Menu](http://katpyxa.info/software/PTU_Reader/Menu.png "Menu") 
4. Choose what images you want to load (see their description below) and click *OK*.

## Output #1: lifetime ordered stack

This is 8-bit stack containing 4096 frames. Each frame corresponds to a lifetime value. Why 4096? It is number of time registers in PicoQuant module. To get real time value in nanoseconds (ns), you need to know frequency of laser pulses during acquisition. Suppose laser frequency is 40MHz. That means distance between pulses is 1/40x10^6 = 25 ns. That means this period is splitted by 4096 intervals, i.e. time difference between frames = 25/4096 ~ 6.1 picoseconds (ps, 10^-12).

The intensity of pixel at *x*,*y* position corresponds to the number of photons with this lifetime during the *whole acquisition*.  
Don't forget to do *Image->Adjust->Brightness/Contrast* to see the signal.

For example, to get a FLIM decay curve of some image area that would look like this:  
![Curve example](http://katpyxa.info/software/PTU_Reader/Curve_example.png "curve")  

select rectangular ROI and go to *Image->Stacks->Plot Z-axis profile*. You can make plot *y*-axis logarithmic by clicking *More>>Set Range..* 

## Output #2: Intensity and Lifetime Average stacks
Lifetime acquisition often happens over multiple frames. If this option is checked in the menu, plugin will provide intensity stack for each frame (if binning is 1) or summarized intensity of multiple frames for the bin size larger than 1.  

In addition, for the same number of binned frames, it will generate average lifetime value map.  
**Important**: the lifetime value is in the same 4096 register format! Use the same math/laser frequency recalculation to get value in nanoseconds. Whole image/stack math can be done using ImageJ *Process->Math* options.

So you can observe average lifetime change during acquisition. It is recommended to you different LUT to highlight its changes (*Image->Lookup Tables->Spectrum* or *Rainbow RGB*)

These two stachs are in 32-bit format.

---
Developed in [Cell Biology group](http://cellbiology.science.uu.nl/) of Utrecht University.  
Email katpyxa @ gmail.com for any questions/comments/suggestions.

