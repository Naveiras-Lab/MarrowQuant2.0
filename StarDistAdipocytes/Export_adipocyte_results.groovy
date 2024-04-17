/* = CODE DESCRIPTION =
 * Export Per-Annotation and Per-Adipocyte Results
 * 
 * == INPUTS ==
 * The image should contain annotations, each with detections that have been classified using 
 * 'Classify Adipocytes by Area.groovy'
 * 
 * == OUTPUTS ==
 * This script creates two files 'annotations.txt' and 'adipocytes.txt' in a 'results' folder
 * inside the current project. The files contain the requested results.
 * NOTE: If this script is run multiple times, the resuts keep getting appended. 
 * To start from scratch, you MUST delete 'annotations.txt' and 'adipocytes.txt'
 *
 * = DEPENDENCIES =
 * You need to install the QuPath Extension BIOP https://github.com/BIOP/qupath-extension-biop
 * You can also get it from the MarrowQuant2.0 Releases page: https://github.com/Naveiras-Lab/MarrowQuant2.0/releases
 * 
 * = INSTALLATION = 
 * After you have installed the extension, nothing else is needed
 * 
 * = AUTHOR INFORMATION =
 * Code made by Olivier Burri, EPFL - SV - PTECH - BIOP 
 * for Rita Sarkis, UPNAVEIRAS
 * 2020.01.20
 * 
 * Last tested on QuPath 0.5.1
 * = COPYRIGHT =
 * Due to the simple nature of this code, no copyright is applicable
 */

// Need the micron symbol
def um = GeneralTools.micrometerSymbol()

// Get the measurements we want to export and the classes we want to count
def measAnnots = ["Total Adip Area "+um+"^2", "Num Detections"]
def classNames = ["Very Small", "Small", "Medium", "Large", "Very Large" ]

// Append class names
measAnnots.addAll( classNames.collect{ "Num "+it } )
measAnnots.addAll( classNames.collect{ "% "+it+" Adips" } )
def measAdips = ["Parent", "Class", "Area "+um+"^2"]

// Prepare export

// Build the results directory
def res_directory = buildFilePath(PROJECT_BASE_DIR, "results" )

// Make sure the folder exists
mkdirs( res_directory )

// Build the results files
def annot_file = new File( res_directory, "annotations.txt" )
def adip_file = new File( res_directory, "adipocytes.txt" )

// Finallz send the results
Results.sendResultsToFile(measAnnots,  getAnnotationObjects() , annot_file)
Results.sendResultsToFile(measAdips,  getDetectionObjects(), adip_file)


// Required imports
import qupath.ext.biop.utils.*
    