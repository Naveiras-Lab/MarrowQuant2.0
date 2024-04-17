/* = CODE DESCRIPTION =
 * Classify Adipoctes in each annotation by category
 * 
 * == INPUTS ==
 * The image should contain annotations, each with detections that will be classified
 * 
 * == OUTPUTS ==
 * A new classification for each detection, based on the 'classNames' variable below
 * The annotations will receive the total adpocyte area measuremant and a percentage representing
 * the ratio of the different classes
 *
 * = DEPENDENCIES =
 * None
 * 
 * = INSTALLATION = 
 * None
 * 
 * = AUTHOR INFORMATION =
 * Code made by Olivier Burri, EPFL - SV - PTECH - BIOP 
 * for Rita Sarkis, UPNAVEIRAS
 * 2020.01.20
 * 
 * Last tested on QuPath 0.5.1
 * 
 * = COPYRIGHT =
 * Due to the simple nature of this code, no copyright is applicable
 */

def areaLimits = [0,             500,     900,      2000,    3500, 1e10   ]
def classNames = ["Very Small", "Small", "Medium", "Large", "Very Large" ]
def um = GeneralTools.micrometerSymbol()
    
def annotations = getAnnotationObjects()
getDetectionObjects().each{it.setPathClass(null)}

annotations.each{ annotation ->
    def adips = annotation.getChildObjects().findAll{ it instanceof PathDetectionObject }
    
    

    
    for( int i=1; i< areaLimits.size(); i++ ) {
        adips.findAll{ adip ->
            def value = measurement( adip, "Area "+um+"^2" )
            
            return ( value >= areaLimits[i-1] && value < areaLimits[i] )
        }.each{ it.setPathClass( getPathClass( classNames[i-1] ) ) }
    }
    
    // Add total adip area
    def totalArea = adips.collect{ measurement( it, "Area "+um+"^2" )}.sum()
    if ( totalArea != null ) {
        annotation.getMeasurementList().putMeasurement( "Total Adip Area "+um+"^2", totalArea )
        def nAdip = adips.size()
        classNames.each{ name -> 
            def n = adips.findAll{ it.getPathClass().equals( getPathClass( name ) ) }.size()
            
            annotation.getMeasurementList().putMeasurement( "% "+name+" Adips", n/nAdip *100 )
        }
    }
   
}

fireHierarchyUpdate()

       
import qupath.lib.objects.PathDetectionObject