# Project Wikimaps-Collector
*Build semantic network of Wikipedia articles based on a single search term. JSON output*

## Main Use-Case
Use the Wikipedia API to download information about articles and authors and then build a semantic
network. The resulting graph of Wikipedia articles is printed in JSON format and can be used in 
<https://github.com/mitcci/Wikimaps-Visualization>

## Howto-Run
RelatedResultsFetcher (main-function) the first and only command line argument is the path
to the config file (initially "config/config.properties")

## Output
After running the main class "RelatedResultsFetcher" a file that is named after the search term
is written into the folder "out". Example: Dominique Strauss-Kahn leads to Dominique_Strauss-Kahn.json

## Configuration
* The search is configured in the file "config.properties" in the subfolder "config"


## Database
The fetcher stores some intermediate results in a MySql Database: 
* The database connection is configured in the file "context.xml" in the subfolder "/src/main/resources"
* The required tables can be set up using the script "page_link_revisions_2011-08-10.sql" in the 
directory "db_scripts"

## Initial Autor
Github-User: ret0