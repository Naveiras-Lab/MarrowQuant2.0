/* = CODE DESCRIPTION =
 * Run StarDist Model from trained adipocytes on selected RGB image
 * 
 * == INPUTS ==
 * The desired model file, for this project, called 'rita-round2_downsampled_adipocytes_r32_p128_g1_e400_se100_b16_aug.pb'
 * 
 * == OUTPUTS ==
 * A series of detection on QuPath with the adpocytes
 * 
 * = DEPENDENCIES =
 * You need the StarDist Extension from QuPath: https://github.com/qupath/qupath-extension-stardist
 * 
 * = INSTALLATION = 
 * If you have installed the StarDist Extension, nothing more is needed
 * 
 * = AUTHOR INFORMATION =
 * Code adapted by Olivier Burri, EPFL - SV - PTECH - BIOP 
 * for Rita Sarkis, UPNAVEIRAS
 * 2022.03.18
 * 
 * = COPYRIGHT =
 * Due to the simple nature of this code, no copyright is applicable
 */
 
def pathModel = "/Users/ritasarkis/Desktop/QuPath_Common_Data_0.4.3/MQ2.0/MarrowQuant2.0/rita-round2_downsampled_adipocytes_r32_p128_g1_e400_se100_b16_aug.pb"
// Build the StarDist Model
def stardist = StarDist2D.builder(pathModel)
        .threshold(0.57)              // Probability (detection) threshold
        .normalizePercentiles(1, 99.8) // Percentile normalization
        .pixelSize(0.9093)              // Resolution for detection
//        .tileSize(2048)              // Specify width & height of the tile used for prediction
//        .cellExpansion(5.0)          // Approximate cells based upon nucleus expansion
//        .cellConstrainScale(1.5)     // Constrain cell expansion using nucleus size
        .ignoreCellOverlaps(false)   // Set to true if you don't care if cells expand into one another
        .measureShape()              // Add shape measurements
        .measureIntensity()          // Add cell measurements (in all compartments)
        .includeProbability(true)    // Add probability as a measurement (enables later filtering)
       // .nThreads(4)                 // Limit the number of threads used for (possibly parallel) processing
//        .simplify(1)                 // Control how polygons are 'simplified' to remove unnecessary vertices
        .doLog()                     // Use this to log a bit more information while running the script
        .build()
        
        
// Run detection for all annotations
def pathObjects = getAnnotationObjects()
def imageData = getCurrentImageData()

stardist.detectObjects(imageData, pathObjects)
println "StarDist Detection done!"


// Imports
import qupath.ext.stardist.StarDist2D

