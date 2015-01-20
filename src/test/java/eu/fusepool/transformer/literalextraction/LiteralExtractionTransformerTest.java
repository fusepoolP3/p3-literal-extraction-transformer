package eu.fusepool.transformer.literalextraction;

import static eu.fusepool.transformer.literalextraction.LiteralExtractionTransformer.PARM_TRANSFORMER;
import static org.apache.clerezza.rdf.core.serializedform.SupportedFormat.TURTLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.activation.MimeType;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.clerezza.rdf.ontologies.DCTERMS;
import org.apache.clerezza.rdf.ontologies.SKOS04;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ResponseBodyData;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;

import eu.fusepool.p3.transformer.server.TransformerServer;
import eu.fusepool.p3.vocab.FAM;
import eu.fusepool.p3.vocab.TRANSFORMER;

public class LiteralExtractionTransformerTest {
    

    private static final int MAX_TIMEOUT = 30000;
    private static final int MIN_TIMEOUT = 5000;
    private static final int MAX_RETRY = 10;
    private static final int RETRY_WAIT = MIN_TIMEOUT/MAX_RETRY;

    private static final Logger log = LoggerFactory.getLogger(LiteralExtractionTransformerTest.class);

    private static final String[] RDF_DATASET_FILES = new String[]{
        "unescothes-part1.ttl","unescothes-part2.ttl","unescothes-part3.ttl",
        "unescothes-part4.ttl"};

    private static final Set<String> EXPECTED_SUPPORTED_INPUT_FORMATS = new HashSet<String>();
    private static final Set<String> EXPECTED_SUPPORTED_OUTPUT_FORMATS = new HashSet<String>();

    static {
        for(MimeType mime : LiteralExtractionTransformer.INPUT_FORMATS){
            EXPECTED_SUPPORTED_INPUT_FORMATS.add(mime.toString());
        }
        for(MimeType mime : LiteralExtractionTransformer.OUTPUT_FORMATS){
            EXPECTED_SUPPORTED_OUTPUT_FORMATS.add(mime.toString());
        }
    }

    private static List<byte[]> RDF_DATASET_CONTENTS;
    private static String BASE_URI;
    private static UriRef BASE_URI_REF;

    private static final Parser parser = Parser.getInstance();
    //TODO: currently this uses a stanbol chain ... we need to write a dummy
    //      transformer that can be called by the literal extraction transformer
    //      for testing purpose
    private static final String UNIT_TEST_TRANSFORMER = "http://localhost:8080/transformers/chain/dbpedia-fst-linking";

    @BeforeClass
    public static void setUp() throws Exception {
        //init the transformer
        final int port = findFreePort();
        BASE_URI = "http://localhost:" + port + "/";
        BASE_URI_REF = new UriRef(BASE_URI);
        TransformerServer server = new TransformerServer(port, true);
        server.start(new LiteralExtractionTransformer());
        
        //init the CSV content test data
        ClassLoader cl = LiteralExtractionTransformerTest.class.getClassLoader();
        RDF_DATASET_CONTENTS = new ArrayList<>(RDF_DATASET_FILES.length);
        for(String dataset : RDF_DATASET_FILES){
            InputStream in = cl.getResourceAsStream(dataset);
            try {
                RDF_DATASET_CONTENTS.add(IOUtils.toByteArray(in));
            } finally {
                IOUtils.closeQuietly(in);
            }
        }
    }

    @Test
    public void turtleOnGet() {
        String accept = "text/turtle";
        Response response = RestAssured.given().header("Accept", accept)
                .expect().statusCode(HttpStatus.SC_OK)
                .header("Content-Type", accept).when().get(BASE_URI);
        
        Graph graph = parser.parse(
                response.getBody().asInputStream(), 
                response.getContentType());
        //Assert supported INPUT and OUTPUT formats
        Set<String> expected = new HashSet<String>(EXPECTED_SUPPORTED_INPUT_FORMATS);
        Iterator<Triple> it = graph.filter(BASE_URI_REF, TRANSFORMER.supportedInputFormat, null);
        while(it.hasNext()){
            Resource r = it.next().getObject();
            assertTrue(r instanceof Literal);
            assertTrue(expected.remove(((Literal)r).getLexicalForm()));
        }
        assertTrue(expected.isEmpty());
        
        expected = new HashSet<String>(EXPECTED_SUPPORTED_OUTPUT_FORMATS);
        it = graph.filter(BASE_URI_REF, TRANSFORMER.supportedOutputFormat, null);
        while(it.hasNext()){
            Resource r = it.next().getObject();
            assertTrue(r instanceof Literal);
            assertTrue(expected.remove(((Literal)r).getLexicalForm()));
        }
        assertTrue(expected.isEmpty());
    }

    @Test
    public void testPost() throws Exception {
        log.info("> test LiteralExtractionTransformer");
        String contentLocation = "http://www.test.org/fusepool/transformer/literalExtraction/";
        String acceptType = TURTLE;
        
        ResponseBodyData result = validateAsyncTransformerRequest(BASE_URI,
                Arrays.asList(PARM_TRANSFORMER,UNIT_TEST_TRANSFORMER),
                TURTLE + ";charset=UTF-8", RDF_DATASET_CONTENTS.get(0), contentLocation, acceptType);
        Graph graph = parser.parse(result.asInputStream(), acceptType);
        log.info("Extraction Results");
        Iterator<Triple> it = graph.filter(null, FAM.entity_reference, null);
        while(it.hasNext()){
            log.info(" - {}",it.next());
        }
        it = graph.filter(null, FAM.topic_reference, null);
        while(it.hasNext()){
            log.info(" - {}",it.next());
        }
    }
    
    /**
     * Helper method that sends an transformer request to the postURI using the
     * parsed content-type and content and requesting the parsed accept header.
     * <p>
     * This will send the initial transformation request and assert that is was
     * accepted. After that it will try to obtain the results for a minimum of
     * 30sec and maximum of 60sec by polling the Job URI every 3sec.<p>
     * Every response is validated and - if available - the final results are
     * returned in the form of {@link ResponseBodyData}. <p>
     * <b>TODO:</b> THis is a generally useful utility and SHOULD BE moved to
     * some transformer test module
     * @param postURI the URI to post to
     * @param contentType the content type
     * @param content the content
     * @param contentLocation the content location
     * @param acceptType the accept content type
     * @return the response data
     * @throws InterruptedException
     */
    public static ResponseBodyData validateAsyncTransformerRequest(String postURI,
            List<String> queryParams, String contentType, byte[] content, 
            String contentLocation, final String acceptType) throws InterruptedException {
        StringBuilder requestUri = new StringBuilder(postURI);
        if(!queryParams.isEmpty()){
            int i=0;
            while(queryParams.size() > i+1){
                requestUri.append(i== 0 ? '?' :'&');
                try {
                    requestUri.append(URLEncoder.encode(queryParams.get(i++), "UTF-8"));
                    requestUri.append('=');
                    requestUri.append(URLEncoder.encode(queryParams.get(i++), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        log.info("> request URI: {}",requestUri);
        //(1) send the transformer request
        RequestSpecification reqSpec = RestAssured.given();
        if(acceptType != null){
            reqSpec.header("Accept", acceptType);
        }
        if(contentLocation != null){
            reqSpec.header("Content-Location", contentLocation);
        }
        if(contentType != null){
            reqSpec.contentType(contentType);
        }
        ResponseSpecification resSpec = reqSpec.body(content).expect();
        resSpec.statusCode(HttpStatus.SC_ACCEPTED);
        Response response = resSpec.when().post(requestUri.toString());
        String location = response.getHeader("location");
        log.info(" - accepted with location: {}", location);
        
        //(2) try for min 30sec max 60sec to retrieve the transformation results
        String locationUri = (postURI.charAt(postURI.length() - 1) == '/' ? 
                postURI.substring(0, postURI.length()-1) : postURI) + location;
        UriRef locationUriRef = new UriRef(locationUri);
        
        long start = System.currentTimeMillis();
        int retry = 0;
        for(; retry < MAX_RETRY && System.currentTimeMillis() - start < MAX_TIMEOUT; retry++){
            response = RestAssured.given().header("Accept", acceptType)
                    .when().get(locationUri);
            int status = response.getStatusCode();
            if(status >= 200 && status < 300){
                long duration = System.currentTimeMillis()-start;
                String respContentType = response.getHeader("Content-Type");
                //log.debug("response-body: {}", response.getBody().print());
                if(response.getStatusCode() == HttpStatus.SC_ACCEPTED){
                    log.info(" ... assert Acceptd (after {}ms)", duration);
                    Graph graph = parser.parse(
                            response.getBody().asInputStream(), 
                            response.getContentType());
                    assertEquals(1, graph.size());
                    assertTrue(graph.contains(new TripleImpl(
                            locationUriRef, TRANSFORMER.status, TRANSFORMER.Processing)));
                } else if(response.getStatusCode() == HttpStatus.SC_OK){
                    log.info(" ... assert Results (after {}ms)", duration);
                    //assert that the content-type is the accept header
                    assertTrue(respContentType != null && respContentType.startsWith(
                            acceptType));
                    return response.getBody();
                } else {
                    fail("Unexpected 2xx status code " + response.getStatusCode()
                        + " for request in "+ locationUri + "!");
                }
            } else {
                if(response.getBody() != null){
                    log.error("ResponseBody: \n {}",response.getBody().print());
                }
                fail("Unexpceted Response Code " + response.getStatusLine()
                        + " for Request on "+ locationUri+ "!");
            }
            synchronized (postURI) {
                postURI.wait(RETRY_WAIT);
            }
        }
        //NOTE: uncomment for debugging so that the server is not killed as the
        //      timeout is over ...
        //synchronized (postURI) {
        //    postURI.wait();
        //}
        fail("Timeout after " + retry + "/"+MAX_RETRY+" rtries and/or " 
                + (System.currentTimeMillis()-start)/1000 + "/"+(MAX_TIMEOUT/1000)+"sec");
        return null;
    }

    public static int findFreePort() {
        int port = 0;
        try (ServerSocket server = new ServerSocket(0);) {
            port = server.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("unable to find a free port");
        }
        log.info(" - run transformer Tests on port {}",port);
        return port;
    }
    
    public static void main(String[] args) {
        Literal l1 = new PlainLiteralImpl("test", new Language("EN"));
        Literal l2 = new PlainLiteralImpl("test", new Language("en"));
        Assert.assertEquals(l1, l2);
    }
    
}
