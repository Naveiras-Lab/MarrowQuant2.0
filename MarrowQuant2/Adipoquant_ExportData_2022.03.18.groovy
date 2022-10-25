import qupath.ext.biop.utils.*


selectDetections();
runPlugin('qupath.lib.plugins.objects.ShapeFeaturesPlugin', '{"area": false,  "perimeter": true,  "circularity": true,  "useMicrons": true}');

def um = GeneralTools.micrometerSymbol()

def columns = ["Adipocyte Index", "Parent", "Area "+um+"^2"]
    

def resultsfolder = buildFilePath(PROJECT_BASE_DIR, "results")
mkdirs( resultsfolder )

def resultsfile = new File(resultsfolder, "adipocyte-measurements.txt")
println(resultsfile.getAbsolutePath())


def summaryfile = new File(resultsfolder, "adipocyte-summary.txt")

def detections = getDetectionObjects()
Results.sendResultsToFile(columns, detections,  resultsfile)


def annotations = getAnnotationObjects().findAll{ it.getPathClass() == getPathClass( "Tissue Boundaries" ) }
def columnsSummary = ["Class", "Total Adipocyte Area "+um+"^2"]


Results.sendResultsToFile( columnsSummary, annotations, summaryfile )

println("Completed")