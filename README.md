# Literal Extraction Transformer

The Literals Extraction Transformer provides a [Fusepool P3](http://p3.fusepool.eu/) 
[Transformer](https://github.com/fusepoolP3/overall-architecture/blob/master/transformer-api.md) 
implementation for enriching a RDF dataset with iformation extracted from long
literals. A typical example is to extract the spatial context and near points of
interest from the textual description of an entity. So might the textual description
of a station or hotel mention the nearest bus stop. But this also works for other
domain like to extract the musical artist, the orchestra and conductor based
on the description of a concert; Points of Interest mentioned in the description
of a hiking tour ...

The Literal Extraction Transformer implements the following workflow:

* parse the RDF dataset from the request POST data
* iterate over triples with the configured literal predicates
    * collect `[{subject},{lang}]` -> `{literal}` pairs
* send transformation requests for the collected textual descriptions to the 
configured Information Extraction Transformer.
    * The configured Information Extraction Transformer need to accept `text/plain` 
    and input and return [Fusepool Annotation Model](https://github.com/fusepoolP3/overall-architecture/blob/master/wp3/fp-anno-model/fp-anno-model.md) 
    (FAM) data as RDF.
* process extraction results for referenced entities and assigned topics and adds
them as triples to the dataset.

The Literals Extraction Transformer will generate multiple transformation
request to the configured Literal Extraction Transformer. As this process in 
expected to require considerable processing time the Literal Extraction Transformer 
uses the asynchronous Transformer workflow.

Installation:
-----

This module builds a runnable jar. After the build succeeds you can find the
runable jar in the ´/target´ folder.

Calling

    java -jar -Xmx2g literal-extraction-transformer-*.jar -p 8080

will run the Literal Extraction Transformer on port `8080`

The command line tool provides the following configuration parameters:

    usage: java -Xmx{size} -jar {jar-name} [options]
    Literal Extraction Transformer:

         -h,--help                display this help and exit
         -p,--port <arg>          the port for the Any23 transformer (default:
                                  8080)
         -t,--thread-pool <arg>   The number of threads usedto process requests
                                  (default: 10).
    provided by Fusepool P3

### Memory Requirements

The Transformer holds the parsed dataset in an in-memory graph until processing is completed. After completion the data as serialized to a temporary file and kept their for the user to request them. The transformer itself only needs a very little memory so the maximum assigned memory only depends on the number and the size of parsed datasets.

Usage:
-----

As the Literal Extraction transformer implements the [Fusepool Transfomer API]
(https://github.com/fusepoolP3/overall-architecture/blob/master/transformer-api.md) 
communication is expected as specified by the Fusepool.

### Supported Input/Output Formats

The capabilities of a transformer can be requested by a simple GET request at 
the base URI. The following listing shows the response of the Literal Extraction
Transformer running at localhost at port `8087`

    curl http://localhost:8087/

    <http://localhost:8087/>
        <http://vocab.fusepool.info/transformer#supportedInputFormat>
            "text/turtle"^^<http://www.w3.org/2001/XMLSchema#string> , 
            "application/rdf+xml"^^<http://www.w3.org/2001/XMLSchema#string> , 
            "text/rdf+nt"^^<http://www.w3.org/2001/XMLSchema#string> , 
            "application/n-quads"^^<http://www.w3.org/2001/XMLSchema#string> , 
            "application/ld+json"^^<http://www.w3.org/2001/XMLSchema#string> ;
        <http://vocab.fusepool.info/transformer#supportedOutputFormat>
            "text/turtle"^^<http://www.w3.org/2001/XMLSchema#string> .

As the response shows the LiteralExtractionTransformer accepts any RDF data and
returns the enriched RDF data as text/turtle`. Other formats can not be supported
as the current implementation stores the enriched dataset as already serialized
content in a temporary file.

### Request Parameters

The Literal Extraction Trnasformer supports the following request parameter

* __transformer__ _(`1..1`)_: The URI of the trnasformer used to extract information from
long linteral value. The parsed transformer needs to accept `text/plain` and 
support `text/turtle` as response format. If this is not the case the request
will not be accepted.
* __lit-pred__ _(`0..n`, default: `rdfs:comment`, `skos:note`, `skos:definition`, 
`schema:description`)_: The URIs of predicates of literals used to extract
information from. If not present the defaults will be used.
* __entity-pred__ _(`0..1`, default: `fam:entity-reference`)_: The predicate used
to add entities extracted from the textual description to the dataset.
* __topic-pred__ _(`0..1`, default: `fam:topic-reference`)_: The predicate used
to add topics assigned based on the textual description to the dataset 

### Asynchronous Transformation Requests

A typical request will look like the follows

    curl -v -X "POST" -H "Content-Type: text/turtle;charset=UTF-8" \
        -T "myDataset.ttl" \
        "http://localhost:8087/?transformer=http%3A%2F%2Flocalhost%3A8088%2F"

This will send the RDF data contained in the `myDataset.ttl` file to the 
LiteralExtraction transformer running at `http://localhost:8087`. Literals of
this dataset will be analysed by using a transformer running at 
`http://localhost:8088/`. For Literal predicates, the referenced entity predicate
and assigend topic predicate the default values will be used.

NOTE that the "Content-Location" header is not used by the Literal Extraction
Transformer implementation.

As the Literal Extraction Transformer is asynchronous it will validate the
parsed RDF data and transformer and immediately return with `202 Accepted`
and the `Location` of the accepted Job

    < HTTP/1.1 202 Accepted
    < Date: Fri, 07 Nov 2014 09:09:42 GMT
    < Location: /job/1678699a-ed36-4282-aaf8-1823aea19970
    < Transfer-Encoding: chunked
    < Server: Jetty(9.2.z-SNAPSHOT)

The returned location can be used by the client to request the status of the job and
to retrieve the final result as soon as it is available. 

    curl http://localhost:8087/job/1678699a-ed36-4282-aaf8-1823aea19970

As soon as the extraction is finished for all resources in the parsed dataset the
enriched RDF data are returned as `text/trutle`.

Example
-------

This example aims to visualize the enrichment of an RDF dataset processed by the Literal Extraction Transformer.

Lets assume a small dataset consisting of two RDFS resources

    @prefix <http://www.example.org/fusepool-p3#> .
    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
    
    :res-1 rdfs:label "Poppi Castle"@en;
        rdfs:comment "Poppi Castle is a medieval castle in Poppi, Tuscany, Italy, formerly the property of the noble family of the Conti Guidi";

The Literal Extraction Transformer will now extract entities from the `rdfs:comment` of `:res-1` and enrich the dataset with the extraction results as shown in the following listing.

    @prefix <http://www.example.org/fusepool-p3#> .
    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
    @prefix fam: <http://vocab.fusepool.info/fam#> .
    @prefix dbr: <http://dbpedia.org/resource/> .
    @prefix dbc: <http://dbpedia.org/resource/Category:> .    

    :res-1 rdfs:label "Poppi Castle"@en;
        rdfs:comment "Poppi Castle is a medieval castle in Poppi, Tuscany, Italy, formerly the property of the noble family of the Conti Guidi";
        fam:entity-reference dbr:Italy, dbr:Tuscany, dbr:Poppi, dbr:Poppi_Castle;
        fam:topic-reference  dbc:Tuscany .

The above listing shows that the Information Extraction Transformer called by the Literal Extraction Transformer was able to extract four entities (Italy, Tuscany, Poppi and the Castle of Poppi) and a two Topics (Tuscany, Castles) from the `rdfs:comment` of the resource `:res-1`. Those information extraction results where used to enrich the original dataset. By default `fam:entity-reference` is used to link extracted entities and `fam:topic-reference` for assigned topics. However this can be customized by parsing different properties in the transformation request.

    
    

