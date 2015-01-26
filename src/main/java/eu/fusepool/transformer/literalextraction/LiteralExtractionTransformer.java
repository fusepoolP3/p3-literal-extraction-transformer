package eu.fusepool.transformer.literalextraction;


import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.servlet.http.HttpServletRequest;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.PlainLiteral;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fusepool.p3.transformer.AsyncTransformer;
import eu.fusepool.p3.transformer.HttpRequestEntity;
import eu.fusepool.p3.transformer.client.Transformer;
import eu.fusepool.p3.transformer.client.TransformerClientImpl;
import eu.fusepool.p3.transformer.commons.Entity;
import eu.fusepool.p3.transformer.commons.util.InputStreamEntity;
import eu.fusepool.p3.vocab.FAM;

public class LiteralExtractionTransformer implements AsyncTransformer, Closeable {


    private final Logger log = LoggerFactory.getLogger(getClass());
    
    public static final int POOL_SIZE = 10;

    private static final Charset UTF8 = Charset.forName("UTF-8");


    public static final MimeType RDF_XML;
    public static final MimeType TURTLE;
    public static final MimeType N_TRIPLE;
    public static final MimeType N_TRIPLE2;
    public static final MimeType N_QUADS;
    public static final MimeType N3;
    public static final MimeType JSON_LD;
    final static MimeType TXT_PLAIN_UTF8;
    final static MimeType TXT_PLAIN;
    final static MimeType TURTLE_UTF8;

    public static final MimeType OUTPUT;


    Parser parser = Parser.getInstance();
    Serializer serializer = Serializer.getInstance();
    
    static {
        try {
            //supported input types
            RDF_XML = new MimeType(SupportedFormat.RDF_XML);
            TURTLE = new MimeType(SupportedFormat.TURTLE);
            N_TRIPLE = new MimeType(SupportedFormat.N_TRIPLE);
            N_TRIPLE2 = new MimeType("application/n-triples");
            N_QUADS = new MimeType("application/n-quads");
            N3 = new MimeType(SupportedFormat.N3);
            JSON_LD = new MimeType("application/ld+json");
            
            //used output type
            OUTPUT = new MimeType(SupportedFormat.TURTLE +";charset="+UTF8);
            
            //used to validate supported input types of the parsed transformer
            TXT_PLAIN = new MimeType("text/plain");
            
            //used when communicating with the aprsed transformer
            TXT_PLAIN_UTF8 = new MimeType("text/plain; charset="+UTF8.name());
            TURTLE_UTF8 = new MimeType(SupportedFormat.TURTLE + "; charset="+UTF8.name());
            
        } catch (MimeTypeParseException e) {
            throw new IllegalStateException(e);
        }
    }
    
    public static final Set<MimeType> INPUT_FORMATS;
    public static final Set<MimeType> OUTPUT_FORMATS;

    public static final String PARAM_TRANSFORMER = "transformer";
    public static final String PARAM_LITERAL_PREDICATE = "lit-pred";
    public static final String PARAM_ENTITY_PREDICATE = "entity-pred";
    public static final String PARAM_TOPIC_PREDICATE = "topic-pred";
    
    static {
        Set<MimeType> formats = new HashSet<MimeType>();
        formats.add(RDF_XML);
        formats.add(TURTLE);
        formats.add(N_TRIPLE);
        formats.add(N_TRIPLE);
        formats.add(N_QUADS);
        formats.add(JSON_LD);
        INPUT_FORMATS = Collections.unmodifiableSet(formats);
        formats = new HashSet<MimeType>();
        formats.add(TURTLE);
        OUTPUT_FORMATS = Collections.unmodifiableSet(formats);
    }
    
    protected final Set<String> activeRequests = new HashSet<String>();
    protected final ReadWriteLock requestLock = new ReentrantReadWriteLock();

    private CallBackHandler callBackHandler;

    /**
     * The producer of 
     */
    private ForkJoinPool datasetExecutor;
    
    private Properties config;
    
    int poolSize = POOL_SIZE;

    private Set<UriRef> predicates;

    
    public LiteralExtractionTransformer() {
        this(new Properties(System.getProperties()));
    }
    
    /**
     * Constructor that allows to customize the Any23 configuration. override/extend parameters of the Any23
     * default configuration
     * @param config the configuration
     */
    public LiteralExtractionTransformer(Properties config) {
        this(config,null);
    }
    /**
     * 
     * @param config TODO: currently not used
     * @param predicates the literal predicates or <code>null</code> to use the
     * {@link Defaults#DEFAULT_LITERAL_PREDICATES}
     */
    public LiteralExtractionTransformer(Properties config, Set<UriRef> predicates) {
        log.info("> create {} transformer ",getClass().getSimpleName());
        this.config = config == null ? new Properties(System.getProperties()) : config;
        this.predicates = predicates;
        
    }

    /**
     * Getter for the core thread pool size
     * @return
     */
    public int getCorePoolSize() {
        return poolSize;
    }
    /**
     * Setter for the core thread pool size
     * @param corePoolSize
     * @throws IllegalStateException if the transformer was already started
     */
    public void setThreadPoolSize(int corePoolSize) {
        if(datasetExecutor != null){
            throw new IllegalStateException("Transformer already started");
        }
        this.poolSize = corePoolSize;
    }
    /**
     * Getter for the maximum thread pool size
     * @return
     */
    public int getPoolSize() {
        return poolSize;
    }

    @Override
    public Set<MimeType> getSupportedInputFormats() {
        return INPUT_FORMATS;
    }

    @Override
    public Set<MimeType> getSupportedOutputFormats() {
        return OUTPUT_FORMATS;
    }

    @Override
    public void activate(CallBackHandler callBackHandler) {
        this.callBackHandler = callBackHandler;
        datasetExecutor = new ForkJoinPool(poolSize);
    }

    @Override
    public void transform(HttpRequestEntity entity, String requestId)
            throws IOException {
        log.info("> transform request {}", requestId);
        log.info(" - mime: {}",entity.getType());
        log.info(" - contentLoc: {}",entity.getContentLocation());
        //syncronously check the request
        //first parse the parsed RDF
        MGraph dataset = new SimpleMGraph();
        parser.parse(dataset, entity.getData(), entity.getType().toString());
        //get the transformer to forward requests to from the query parameter
        //2nd get the parsed transformer
        HttpServletRequest request = entity.getRequest();
        String transformerUri = request.getParameter(PARAM_TRANSFORMER);
        if(transformerUri == null){
            throw new IllegalArgumentException("The required " + PARAM_TRANSFORMER 
                    + "is not present in the request!");
        } else {
            transformerUri = URLDecoder.decode(transformerUri, UTF8.name());
        }
        log.info(" - transformer: {}",transformerUri);
        Transformer transformer = new TransformerClientImpl(transformerUri);
        if(!checkTypesCompatible(transformer.getSupportedInputFormats(),TXT_PLAIN)){
            throw new IllegalArgumentException("The parsed Transformer " + transformerUri 
                    + "does not support the required input MimeType "+ TXT_PLAIN + "!");
        }
        if(!checkTypesCompatible(transformer.getSupportedOutputFormats(), TURTLE)){
            throw new IllegalArgumentException("The parsed Transformer " + transformerUri 
                    + "does not support the required output MimeType "+ TURTLE + "!");
        }
        //look for configured literal property URLs
        String[] literalPredicates = request.getParameterValues(PARAM_LITERAL_PREDICATE);
        Set<UriRef> literalPredUris = null;
        if(literalPredicates != null){
            literalPredUris = new HashSet<>();
            for(String literalPredicate : literalPredicates){
                if(!StringUtils.isBlank(literalPredicate)){
                    literalPredUris.add(new UriRef(URLDecoder.decode(literalPredicate, UTF8.name())));
                }
            }
        }
        if(literalPredUris == null || literalPredUris.isEmpty()){ //if no valid are configured 
            literalPredUris = predicates; //use the default
        }
        log.info(" - literal predicates: {}",literalPredUris);
        LiteralExtractonJob job = new LiteralExtractonJob(requestId, transformer, dataset, literalPredUris);
        //look for a custom referenced entity predicate
        String entityPredicate = request.getParameter(PARAM_ENTITY_PREDICATE);
        if(!StringUtils.isBlank(entityPredicate)){
            job.setReferencedEntityPredicate(new UriRef(
                    URLDecoder.decode(entityPredicate, UTF8.name())));
        }
        log.info(" - referenced entity predicate: {}",job.getReferencedEntityPredicate());
        //look for a custom assigned topic predicate
        String topicPredicate = request.getParameter(PARAM_TOPIC_PREDICATE);
        if(!StringUtils.isBlank(topicPredicate)){
            job.setAssigendTopicPredicate(new UriRef(
                    URLDecoder.decode(topicPredicate, UTF8.name())));
        }
        log.info(" - assigned topic predicate: {}",job.getAssigendTopicPredicate());
        DatasetProcessingTask datasetTask = new DatasetProcessingTask(job);
        requestLock.writeLock().lock();
        try {
            log.info("> schedule dataset extraction[id: {} | transformer: {}]", 
                    new Object[]{requestId, transformerUri});
            datasetExecutor.submit(datasetTask);
            activeRequests.add(requestId);
            //TODO: we need 
        } finally {
            requestLock.writeLock().unlock();
        }
    }

    @Override
    public boolean isActive(String requestId) {
        requestLock.readLock().lock();
        try {
            return activeRequests.contains(requestId);
        } finally {
            requestLock.readLock().unlock();
        }
    }

    protected CallBackHandler getCallBackHandler() {
        return callBackHandler;
    }
    
    @Override
    public void close() throws IOException {
        if(datasetExecutor != null){
            datasetExecutor.shutdown();
            datasetExecutor = null;
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        close();
    }
    /**
     * This method checks if the required {@link MimeType} is covered by the
     * set of supported. This also considers wildcard prime/sub types.
     * @param supported
     * @param required
     * @return
     */
    public static boolean checkTypesCompatible(Set<MimeType> supported, MimeType required){
        for(MimeType t : supported){
            boolean wildCardPrime = "*".equals(t.getPrimaryType());
            boolean wildCardSub = "*".equals(t.getSubType());
            if(wildCardPrime && wildCardSub){
                return true;
            } else if(wildCardPrime && t.getSubType().equalsIgnoreCase(required.getSubType())){
                return true;
            } else if(wildCardSub && t.getPrimaryType().equalsIgnoreCase(required.getPrimaryType())){
                return true;
            } else if(t.getBaseType().equalsIgnoreCase(required.getBaseType())){
                return true;
            }
        }
        return false;
    }

    
    
    class DatasetProcessingTask extends RecursiveAction{
        
        private final LiteralExtractonJob job;
        
        public DatasetProcessingTask(LiteralExtractonJob job) {
            this.job = job;
        }

        @Override
        protected void compute() {
            log.info("> start processing {}",job);
            boolean success = false;
            TmpFileEntity resultEntity = null;
            Exception ex = null;
            try {
                Map<ResourceText, StringBuilder> resourceTexts = new HashMap<ResourceText, StringBuilder>();
                for(UriRef predicate : job.getLiteralPredicates()){
                    for(java.util.Iterator<Triple> it = job.dataset.filter(null, predicate, null); it.hasNext();){
                        Triple t = it.next();
                        if(t.getObject() instanceof PlainLiteral){
                            PlainLiteral text = (PlainLiteral)t.getObject();
                            //TODO: make min literal size configurable
                            if(text.getLexicalForm().length() > 50){
                                //create a new extraction job
                                ResourceText key = new ResourceText(t.getSubject(),text.getLanguage());
                                StringBuilder textBuilder = resourceTexts.get(key);
                                if(textBuilder == null){
                                    resourceTexts.put(key, new StringBuilder(text.getLexicalForm()));
                                } else { //append
                                    textBuilder.append("\n\n").append(text.getLexicalForm());
                                }
                            }
                        }
                    }
                }
                log.debug(" > found {} resource literals for request {}", resourceTexts.size(),job.requestId);
                Collection<LiteralExtractionTask> extractionTasks = new ArrayList<>(resourceTexts.size());
                for(Entry<ResourceText, StringBuilder> entry : resourceTexts.entrySet()){
                    LiteralExtractionTask extractionTask = new LiteralExtractionTask(job,
                            entry.getKey(). resource, entry.getValue().toString(),
                            entry.getKey().lang);
                    extractionTasks.add(extractionTask);
                }
                long start = System.currentTimeMillis();
                invokeAll(extractionTasks);
                long dur = System.currentTimeMillis() - start;
                log.info(" ... invoked {} in {}ms ({}ms avrg", new Object[]{
                        extractionTasks.size(), dur, (dur*100/extractionTasks.size())/100f});
                //TODO, create Entity with the updated graph
                resultEntity = new TmpFileEntity(job.requestId, TURTLE_UTF8);
                OutputStream out = resultEntity.getWriter();
                try {
                    serializer.serialize(out, job.dataset, SupportedFormat.TURTLE);
                } finally {
                    IOUtils.closeQuietly(out);
                }
                success = true;
            } catch (IOException e){
                log.error("IOException while processing Request "+job,e);
                ex = e;
            } catch (Exception e){
                if(e instanceof InterruptedException){
                    Thread.currentThread().interrupt();  // set interrupt flag
                }
                ex = new RuntimeException("Error while processing Request "+ job, e);
                log.error(" - unable to transform job "+job+" (message: "+ex.getMessage()+")!", ex);
            } finally {
                requestLock.writeLock().lock();
                try {
                    activeRequests.remove(job.requestId);
                    if(success) {
                        getCallBackHandler().responseAvailable(job.requestId, resultEntity);
                    } else {
                        if(ex == null){ //an Error was thrown
                            ex = new RuntimeException("Unknown Error while processing Request"+job);
                        } //else catched Exception
                        getCallBackHandler().reportException(job.requestId, ex);
                    }
                } finally {
                    requestLock.writeLock().unlock();
                    //in any case try to close the source
                }

            }
        }
        
    }
    /**
     * Used as key to collect text literals language for an entity in a given
     * language
     */
    class ResourceText {
        
        final Language lang;
        final NonLiteral resource;
        
        ResourceText(NonLiteral resource, Language lang){
            this.lang = lang;
            this.resource = resource;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((lang == null) ? 0 : lang.hashCode());
            result = prime * result + resource.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ResourceText other = (ResourceText) obj;
            if (lang == null) {
                if (other.lang != null)
                    return false;
            } else if (!lang.equals(other.lang)){
                return false;
            }
            if (!resource.equals(other.resource)) {
                return false;
            }
            return true;
        }
        
    }
    
    
    class LiteralExtractionTask extends RecursiveAction{

        private final LiteralExtractonJob job;
        private final NonLiteral subject;
        private final String text;
        /**
         * NOTE: currently not used as {@link The
         */
        private final Language lang;
        
        private URI contentLocation = null;

        LiteralExtractionTask(LiteralExtractonJob job, NonLiteral subject, String text, Language lang){
            this.job = job;
            this.subject = subject;
            this.text = text;
            this.lang = lang;
            if(subject instanceof UriRef){
                try {
                    contentLocation =  new URI(((UriRef)subject).getUnicodeString());
                } catch (URISyntaxException e){
                    log.debug(" - Unbale to create URI from subject URI {}. Will use random URN instead!", subject);
                }
            }
            if(contentLocation == null){
                //TODO: maybe use the MD5 of the String
                contentLocation = URI.create("urn:fusepool.transformer:literalExtraction:" + UUID.randomUUID());
            }
        }
        
        @Override
        protected void compute() {
            if(log.isDebugEnabled()){
                log.debug("> extract Literals for");
                log.debug("  - subject: {}",subject);
                log.debug("  - text[length: {}] {}",
                        text.length(),
                        text.length() > 50 ? text.substring(0, 47)+"..." : text);
                log.debug("  - lang: {}",lang);
            }
            Entity entity = new InputStreamEntity() {

                @Override
                public MimeType getType() {
                    return TXT_PLAIN_UTF8;
                }
                
                @Override
                public InputStream getData() throws IOException {
                    return new ByteArrayInputStream(text.getBytes(UTF8));
                }
                @Override
                public URI getContentLocation() {
                    return contentLocation;
                }
            };
            try {
                //TODO: I am unable to set the "Content-Language" header to
                //    the language of the Literal (if one is present)
                long start = System.currentTimeMillis();
                Entity response = job.transformer.transform(entity, TURTLE_UTF8);
                log.debug(" ... processed in {}ms", System.currentTimeMillis()-start);
                //(1) parse TURTLE results
                Graph famResults = parser.parse(response.getData(), SupportedFormat.TURTLE);
                //(2) get Informations form the FAM enhancements
                //TODO: Improve processing of FAM results
                SimpleMGraph extractionResults = new SimpleMGraph();
                //for now we are only interested in linked entities
                log.debug("  - Extraction Results: ");
                Iterator<Triple> it = famResults.filter(null, FAM.entity_reference, null);
                while(it.hasNext()){
                    Triple t = it.next();
                    if(t.getObject() instanceof UriRef){
                        NonLiteral entityAnno = t.getSubject();
                        UriRef refEntity = (UriRef)t.getObject();
                        log.debug("      > entity {} for {}", refEntity, subject);
                        extractionResults.add(new TripleImpl(subject,job.getReferencedEntityPredicate(), refEntity));
                    }
                }
                it = famResults.filter(null, FAM.topic_reference, null);
                while(it.hasNext()){
                    Triple t = it.next();
                    if(t.getObject() instanceof UriRef){
                        NonLiteral topicAnno = t.getSubject();
                        UriRef refTopic = (UriRef)t.getObject();
                        log.debug("      > topic {} for {}", refTopic, subject);
                        extractionResults.add(new TripleImpl(subject,job.getAssigendTopicPredicate(), refTopic));
                    }
                }
                //we need to add them (while within a write lock
                job.results.getLock().writeLock().lock();
                try {
                    log.trace("add {} extracted triples to resultGraph", extractionResults.size());
                    job.results.addAll(extractionResults);
                } finally {
                    job.results.getLock().writeLock().unlock();
                }
                log.trace(" ... completed");
            } catch (RuntimeException e){ //while transforming the entity
                Throwable cause = e.getCause();
                if(cause instanceof IOException){
                   log.warn("Unable to extract information for " + subject
                           + " using " + job + "(message: " 
                           + cause.getMessage() + ")", cause);
                } else {
                    log.warn("Unable to extract information for " + subject
                            + " using " + job, e);
                    throw e;
                }
            } catch (IOException e) { //while reading results from stream
                log.warn("Unable to read extraction results for " + subject
                        + " using " + job + "(message: " 
                        + e.getMessage() + ")", e);
            }
        }


        
    }

    
}
