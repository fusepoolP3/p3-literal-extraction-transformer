package eu.fusepool.transformer.literalextraction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;

import eu.fusepool.p3.transformer.HttpRequestEntity;
import eu.fusepool.p3.transformer.SyncTransformer;
import eu.fusepool.p3.transformer.commons.Entity;
import eu.fusepool.p3.vocab.FAM;

public class DummyTransformer implements SyncTransformer {

    protected static final String ENTITY_SUFFIX = "dummyEntity";
    protected static final String TOPIC_SUFFIX = "dummyTopic";
    protected static final String ENTITY_ANNO_SUFFIX = "entityAnno";
    protected static final String TOPIC_ANNO_SUFFIX = "topicAnno";
    private static final String DEFAULT_BASE_URI = "http://www.test.org/fusepool/literalExtractionTransformer/dummyTransformer#";
    private final static MimeType TURTLE;
    private final static MimeType TEXT_PLAIN;
    
    static {
        try {
            TURTLE = new MimeType(SupportedFormat.TURTLE);
            TEXT_PLAIN = new MimeType("text/plain");
        } catch (MimeTypeParseException e) {
            throw new IllegalStateException(e);
        }
    }
    
    Serializer serializer = Serializer.getInstance();
    
    public DummyTransformer() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public Set<MimeType> getSupportedInputFormats() {
        return Collections.singleton(TEXT_PLAIN);
    }

    @Override
    public Set<MimeType> getSupportedOutputFormats() {
        return Collections.singleton(TURTLE);
    }

    @Override
    public Entity transform(HttpRequestEntity entity) throws IOException {
        final MGraph graph = new SimpleMGraph();
        //create the FAM enhancements
        //NOTE: for now we are only creating two triples referring a dummy
        //      Entity and Topic as the LiteralExtractionTransformer does not
        //      use more information of the FAM.
        //      As the LiteralExtractionTransfomer improves this DummyTransformer
        //      will also need to be improved to create a more complete FAM
        //      annotations.
        String baseUri;
        if(entity.getContentLocation() != null){
            baseUri = entity.getContentLocation().toString()+"-";
        } else {
            baseUri = DEFAULT_BASE_URI;
        }
        UriRef entityEnhancement = new UriRef(DEFAULT_BASE_URI + ENTITY_ANNO_SUFFIX);
        UriRef dummyEntity = new UriRef(baseUri + ENTITY_SUFFIX);
        graph.add(new TripleImpl(entityEnhancement, FAM.entity_reference, dummyEntity));

        UriRef topicEnhancement = new UriRef(DEFAULT_BASE_URI + TOPIC_ANNO_SUFFIX);
        UriRef dummyTopic = new UriRef(baseUri + TOPIC_SUFFIX);
        graph.add(new TripleImpl(topicEnhancement, FAM.topic_reference, dummyTopic));
        
        return new Entity() {
            
            @Override
            public void writeData(OutputStream out) throws IOException {
                serializer.serialize(out, graph, SupportedFormat.TURTLE);
            }
            
            @Override
            public MimeType getType() {
                return TURTLE;
            }
            
            @Override
            public InputStream getData() throws IOException {
                return null; /*I think we do not need this*/
            }
            
            @Override
            public URI getContentLocation() {
                return null; /*No content location*/
            }
        };
    }

    @Override
    public boolean isLongRunning() {
        return false;
    }

}
