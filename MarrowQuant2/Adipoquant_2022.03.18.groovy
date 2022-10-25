//guiscript=true

// Default inputs
def aadipMin = 120 
def aadipMax = 150000
 



def aminCir = 0.3
def aexcludeOnEdges = true
def adownsample = 4


// START OF SCRIPT

clearDetections()

def tb = getPathClass("Tissue Boundaries", getColorRGB(0,255,255))
def ar = getPathClass("Artifact", getColorRGB(0,255,255))
def adip_class = getPathClass("Adipocyte", getColorRGB(0,255,255))

def mq_classes = [tb, ar, adip_class]

def all_classes = getQuPath().getAvailablePathClasses().toList()

mq_classes.each { the_class ->
    if (all_classes.find{ it == the_class } != null ) {
        println("Class "+the_class+" already exists")
    } else {
        all_classes.add(the_class)
        println("Creating class "+the_class)
    }
}    

getQuPath().getAvailablePathClasses().setAll(all_classes)
fireHierarchyUpdate()

def ij = IJExtension.getImageJInstance()

ij.show()
def ad = new AdipocyteDetector()

ad.with {
    downsample =  adownsample
    adipMin = aadipMin*downsample
    adipMax =  aadipMax*downsample
    minCir =  aminCir
    excludeOnEdges =  aexcludeOnEdges
    
}

def tissues = getAnnotationObjects().findAll{ it.getPathClass().equals( getPathClass("Tissue Boundaries") ) }

tissues.eachWithIndex{ tissue, i ->
   
    tissue.setLocked(true)
    println(sprintf("Analysing Tissue Boundaries #%d", i) )
    ad.run(tissue)
}

fireHierarchyUpdate()	
def imageName = ServerTools.getDisplayableImageName( getCurrentServer() )//getCurrentImageData().getServer().getShortServerName()
println("Processing complete for " +  imageName );
//ij.quit()
 
// Main class for Adipocyte Detection
class AdipocyteDetector {
    def adipMin = 100
    def adipMax = 50000
    def minCir = 0.0
    def downsample = 2
    def um = GeneralTools.micrometerSymbol()
    public void run(def tissue) {
        Interpreter.batchMode = true 
        IJ.run("Close All")
        
        // Fix Oli QuPath 0.2.0: Need to explicitely insert objects into hierarchy if it was not done        
        // find artifacts
        def artifactAnnotations = getAllObjects().findAll{ it.getPathClass() == getPathClass("Artifact") }
        insertObjects( artifactAnnotations )
        
        
        def image = getImagePlus( tissue, this.downsample )
        

        
        // Pick up pixel size
        def px_size = image.getCalibration().pixelWidth
        
        // Get Tissue 
        def tissue_roi = IJTools.convertToIJRoi( tissue.getROI(), image.getCalibration(), this.downsample )
        
        image.show()
        
        // Extract ROIs
        def overlay = image.getOverlay()
        
        def artifactObjects = tissue.getChildObjects().findAll { it.getPathClass() == getPathClass( "Artifact" ) }
   
        def artifacts = artifactObjects.collect{ IJTools.convertToIJRoi( it.getROI(), image.getCalibration(), this.downsample ) }
        
        IJ.log(""+artifacts)
        
        // Merge all artifacts together.
        def all_edges = uglyArtifactMerge(artifacts, tissue_roi, image)
        
        logger.info("{}", all_edges)
        
        // Creates HSB stacks from the original image
        def hsb_image = image.duplicate()
        IJ.run(hsb_image, "HSB Stack", "")
        hsb_image.show()
        
        // Call Color Deconvolution and recover the images (must be done through GUI for now)
        def deconvolved = colorDeconvolution( image, "H&E" )
        
        // Creates the final image we are going to process from the hue and brightness image obtained
        def ic = new ImageCalculator()
        def adip_raw_image = ic.run("Subtract create", deconvolved[2], deconvolved[0])
        
        adip_raw_image.show()
        logger.info("Done for now")

        image.close()
        deconvolved.each{ it.close() }
        
        def saturation = hsb_image.getStack().getProcessor(2) // Saturation is the second image
        saturation.multiply(8)
        
        ic.run("Add", adip_raw_image, new ImagePlus("TesT", saturation) )
        
        adip_raw_image.show()
        
        IJ.setRawThreshold(adip_raw_image, 0, 127, null);
        
        // Morphological operations and analyze particle
        def adip_mask = new ImagePlus("Adip Mask", adip_raw_image.getProcessor().createMask()) // IB?
        IJ.log( ""+adip_mask.isInvertedLut() )
        if( adip_mask.isInvertedLut() ) adip_mask.getProcessor().invertLut()
        IJ.run( adip_mask, "Invert", "") // IB?
        IJ.run( adip_mask, "Watershed", "" )
        adip_mask.show()
        IJ.run( adip_mask, "Options...", "iterations=50 count=5 pad do=Erode" )

        adip_mask.setRoi( all_edges )
        IJ.setBackgroundColor(255,255,255)
        IJ.run(adip_mask, "Clear Outside", "")
        IJ.setRawThreshold(adip_mask, 0, 127, null)
        adip_mask.killRoi()
        IJ.run(adip_mask, "Analyze Particles...", "size="+this.adipMin+"-"+this.adipMax+" circularity="+this.minCir+"-1.00 show=Nothing exclude add")
        
        // Merge all adips as a single selection
        def rm = RoiManager.getInstance() ?: new RoiManager()
        // Save as Detections in QuPath
        def adips = rm.getRoisAsArray() as List
        rm.reset()    
        rm.close()
        def total_area = 0

	// Measurement of adipocytes areas and displaying of the data in QuPath
        adips.eachWithIndex{ adip, idx ->
            def qu_adip = IJTools.convertToROI( adip, image.getCalibration(), this.downsample, null )
            def det = new PathDetectionObject(qu_adip, getPathClass("Adipocyte"))            
            def area = adip.getStatistics().area
            det.getMeasurementList().putMeasurement( "Adipocyte Index", idx+1 )
            det.getMeasurementList().putMeasurement( "Area "+um+"^2", area *  px_size *  px_size )
            
            tissue.addPathObject(det)
            total_area += area
        }
        
        tissue.getMeasurementList().clear();
        tissue.getMeasurementList().putMeasurement( "Total Adipocyte Area "+um+"^2", total_area *  px_size *  px_size )       
        Interpreter.batchMode = false
        fireHierarchyUpdate()
    }
        
	// Excludes the artifacts from the ROI we want to process
    private Roi uglyArtifactMerge(def artifacts, def tissue_roi, def image) {
        if ( artifacts.isEmpty() ) { 
            return tissue_roi 
        }
        
        if (artifacts.size > 0) {
            
            def rm = RoiManager.getInstance() ?: new RoiManager()
            rm.runCommand("Reset")    
            artifacts.each{ rm.addRoi(it) }
            def all_artifacts
            if ( artifacts.size == 1 ) {
                all_artifacts = artifacts[0]
            } else {
                rm.setSelectedIndexes((0..rm.getCount()-1) as int[])
                rm.runCommand(image, "OR")
                all_artifacts = image.getRoi()
            }
            rm.runCommand("Reset")
            // AND then XOR with tissue
            IJ.log(""+all_artifacts)
            rm.addRoi(all_artifacts)
            rm.addRoi(tissue_roi)
            rm.setSelectedIndexes([0,1] as int[])
            rm.runCommand(image, "AND")
            def overlap_artifacts = image.getRoi()
            rm.addRoi( overlap_artifacts )
            rm.setSelectedIndexes([1,2] as int[])
            rm.runCommand(image, "XOR")
            rm.close()
            return image.getRoi()
        }
    }

    def getImagePlus( def pathObject, def downsample ) {
        def server = getCurrentServer()
        def request = RegionRequest.createInstance( server.getPath(), downsample, pathObject.getROI() )
        def pathImage = IJTools.convertToImagePlus( server, request )
        return pathImage.getImage()
    }
    
    // Use Color Deconvolution Plugin in MarrowQuant
    public ImagePlus[] colorDeconvolution ( ImagePlus image, String stain ) {
        def cd = new Colour_Deconvolution()
        def matList = cd.getStainList()
        def mt = matList.get( stain )
        def stackList = mt.compute( false, true, image )
        // This returns an array of ImageStacks
        
        // Make into an ImagePlus            
        def imageStack = new ImageStack( stackList[0].getWidth(), stackList[0].getHeight() )
        
        stackList.each { imageStack.addSlice( it.getProcessor( 1 ) ) }
        
        def deconvolved = stackList.collect{ new ImagePlus( image.getTitle()+"-"+stain, it ) }
        
        return deconvolved
            
    }
    
}


// Import BIOP library to do fun things

import ij.IJ
import sc.fiji.colourDeconvolution.*
import ij.WindowManager
import ij.plugin.ImageCalculator
import ij.ImagePlus
import ij.process.ImageProcessor
import ij.plugin.frame.RoiManager
import qupath.lib.objects.PathDetectionObject
import qupath.ext.biop.utils.*

import qupath.imagej.tools.*
import qupath.lib.objects.*
import qupath.imagej.objects.*
import qupath.imagej.helpers.*
import qupath.lib.roi.*
import ij.*
import ij.process.*
import ij.measure.Measurements
import ij.gui.*
import ij.macro.Interpreter
import ij.gui.Roi
import qupath.imagej.gui.IJExtension
//import qupath.lib.gui.helpers.DisplayHelpers
import qupath.imagej.helpers.*
//import qupath.lib.roi.PathObjectTools
import qupath.lib.objects.PathAnnotationObject
