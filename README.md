# Literal Extraction Transformer [![Build Status](https://travis-ci.org/fusepoolP3/p3-literal-extraction-transformer.svg)](https://travis-ci.org/fusepoolP3/p3-literal-extraction-transformer)

The Literal Extraction Transformer provides a [Fusepool P3](http://p3.fusepool.eu/) 
[Transformer](https://github.com/fusepoolP3/overall-architecture/blob/master/transformer-api.md) 
implementation for enriching a RDF dataset with information extracted from long
literals. A typical example is to extract the spatial context and near points of
interest from the textual description of an entity. So might the textual description
of a station or hotel mention the nearest bus stop. But this also works for other
domains like extracting musical artists, the orchestra or conductor based
on the description of a concert; Points of Interest mentioned in the description
of a hiking tour ...

The Literal Extraction Transformer implements the following workflow:

* parse the RDF dataset from the request POST data
* iterate over triples with the configured literal predicates
    * collect `[{subject},{lang}]` -> `{literal}` pairs
* send transformation requests for the collected textual descriptions to the 
configured Information Extraction Transformer.
    * The configured Information Extraction Transformer needs to accept `text/plain` 
    as input and return [Fusepool Annotation Model](https://github.com/fusepoolP3/overall-architecture/blob/master/wp3/fp-anno-model/fp-anno-model.md) 
    (FAM) data as RDF.
* process extraction results for referenced entities and assigned topics and add
them as triples to the dataset.

The Literals Extraction Transformer will generate multiple transformation
requests to the configured Literal Extraction Transformer. As this process is
expected to require considerable processing time the Literal Extraction Transformer 
uses the asynchronous Transformer workflow.

## Try it out

First, obtain the latest [release](https://github.com/fusepoolP3/p3-literal-extraction-transformer/releases/latest).

Next, start the transformer:

    java -jar literal-extraction-transformer-*.jar

To obtain the supported input/output-formats of the transformer, query it with the curl-utility:

    curl http://localhost:8305

For advanced testing of the transformer, refer to the section "Usage" just below.

## Compiling and Running

Compile the transformer using maven:

    mvn install

This will build a runnable jar. After the build succeeds you can find the
runable jar in the ´/target´ folder.

Calling

    java -jar -Xmx2g literal-extraction-transformer-*.jar -p 8080

will run the Literal Extraction Transformer on port `8080`

The command line tool provides the following configuration parameters:

    usage: java -Xmx{size} -jar {jar-name} [options]
    Literal Extracttion Transformer:

     -h,--help                display this help and exit
     -P,--Port <arg>          the port for the literal extraction transformer
                              (default: 8305)
     -p,--port <arg>          the port for the literal extraction transformer
                              (default: 8305)
     -t,--thread-pool <arg>   The number of threads usedto process requests
                              (default: 10).
    provided by Fusepool P3

### Memory Requirements

The Transformer holds the parsed dataset in an in-memory graph until processing is completed. After completion the data as serialized to a temporary file and kept there for the user to request them. The transformer itself only needs a very little memory so the maximum assigned memory only depends on the number and the size of parsed datasets.

## Usage

As the Literal Extraction transformer implements the [Fusepool Transfomer API]
(https://github.com/fusepoolP3/overall-architecture/blob/master/transformer-api.md) 
communication is expected to follow this specification.

### Supported Input/Output Formats

The capabilities of a transformer can be requested by a simple GET request at 
the base URI. The following listing shows the response of the Literal Extraction
Transformer running at localhost at port 8305:

    curl http://localhost:8305/

    <http://localhost:8305/>
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

The Literal Extraction Transformer supports the following request parameters:

* __transformer__ _(`1..1`)_: The URI of the transformer used to extract information from
the long literal value. The passed transformer needs to accept `text/plain` and 
support `text/turtle` as response format. If this is not the case the request
will not be accepted.
* __lang__ _(`0..n`, default: any)_: Allows to explicitly define the set of
processed languages. If missing all languages will be processed. _NOTE_ that literals
without language tag will also be processed as their language is assumed to be
unknown (to be determined by a language detection feature of the called transformer).
* __min-lit-len__ _(`0..1`, default: `50`)_: Allows to specify the minimal length of literals to that they are considered for information extraction.
* __lit-pred__ _(`0..n`, default: `rdfs:comment`, `skos:note`, `skos:definition`, 
`schema:description`)_: The URIs of predicates of literals used to extract
information from. If not present the defaults will be used.
* __lang-pred__ _(`0..1`, default: `dct:language`)_: The predicate used to add
the language detected for literals that had not language assigned. 
* __entity-pred__ _(`0..1`, default: `fam:entity-reference`)_: The predicate used
to add entities extracted from the textual description to the dataset.
* __topic-pred__ _(`0..1`, default: `fam:topic-reference`)_: The predicate used
to add topics assigned based on the textual description to the dataset 
* __{ne-type}-ne-pred__ _(since v. 1.1.0)_: Allow to define the predicates used to link extracted 
Named Entities. Four different named entity types (`{ne-type}`) are supported; plus
an additional predicate used for named entities with no (or an unkown) type. Named
Entities are represented by the [FAM](https://github.com/fusepoolP3/overall-architecture/blob/master/wp3/fp-anno-model/fp-anno-model.md)
by `fam:EntityMention` annotation. The named entity is the value of the 
`fam:entity-mention` property. The type of the named entity is the value of the 
`fam:entity-type` property.
    * __pers-ne-pred__ _(`0..1`, default: `fam:preson-ne-reference`)_: The predicate used
    to link to named entities with one of the following types: `dbo:Person`, `schema:Person`, `nerd:Person` and `foaf:Person`
    * __org-ne-pred__ _(`0..1`, default: `fam:organization-ne-reference`)_: The predicate used
    to link to named entities with one of the following types: `dbo:Organisation`, `schema:Organization`, `nerd:Organization` and `foaf:Organization`
    * __loc-ne-pred__ _(`0..1`, default: `fam:location-ne-reference`)_: The predicate used
    to link to named entities with one of the following types: `dbo:Place`, `schema:Place`, `nerd:Location` and `geonames:Feature`
    * __misc-ne-pred__ _(`0..1`, default: `fam:location-ne-reference`)_: The predicate used
    to link to named entities with one of the following types: `skos:Concept` and `schema:Intangible`
    * __unk-ne-pred__ _(`0..1`, default: `fam:named-entity-reference`)_: The predicate used
    to link to named entities with none or an unknown (other as the one listed above) type.
* __keyword-pred__ _(`0..1`, default: `fam:keyword`)_: The predicate used
to add keyword (and keyword phrases) extracted from the parsed text
* __sentiment-pred__ _(`0..1`, default: `fam:sentiment`)_: The predicate used
to add the sentiment (double value in the range `[-1..+1]`) detected for the parsed text

### Asynchronous Transformation Requests

A typical request will look like the follows

    curl -v -X "POST" -H "Content-Type: text/turtle;charset=UTF-8" \
        --data-binary "@myDataset.ttl" \
        "http://localhost:8305/?transformer=http%3A%2F%2Flocalhost%3A8088%2F"

This will send the RDF data contained in the `myDataset.ttl`-file to the 
LiteralExtraction transformer running at `http://localhost:8305`. Literals of
this dataset will be analysed by using a transformer running at 
`http://localhost:8088/`. For Literal predicates, the referenced entity predicate
and assigend topic predicate the default values will be used.

NOTE: the "Content-Location" header is not used by the Literal Extraction
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

    curl http://localhost:8305/job/1678699a-ed36-4282-aaf8-1823aea19970

As soon as the extraction is finished for all resources in the parsed dataset the
enriched RDF data are returned as `text/trutle`.

## Example

This example shows the enrichment of an RDF dataset processed by the Literal Extraction Transformer.

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
        fam:topic-reference  dbc:Tuscany ;
        fam:person-ne-reference "Poppi Castle"@en ;
        fam:location-ne-reference "Poppi"@en .

The above listing shows that the Information Extraction Transformer called by the 
Literal Extraction Transformer was able to extract four entities (Italy, Tuscany, 
Poppi and the Castle of Poppi) and a two Topics (Tuscany, Castles) from 
the `rdfs:comment` of the resource `:res-1`. Those information extraction results where used to enrich the original dataset. 
By default `fam:entity-reference` is used to link extracted entities and 
`fam:topic-reference` for assigned topics. However this can be customized by 
parsing different properties in the transformation request.

_Since version 1.1.0_ also Named Entities are supported. The above Example shows
that the Person `Poppi Castle` (a false positive) and the place `Poppi` are detected
as named entities by the English ixa nerc model for the this sentence. 

Named Entity Recognition support is e.g. provided by the 
[Fusepool Apache Stanbol Launcher](https://github.com/fusepoolP3/p3-stanbol-launcher).
To use the IXA NERC models you need to include the preconfigured `ixa-nerc` engine
in the Enhancement Chain for the transformer configured to the Literal Extraction Transformer.
