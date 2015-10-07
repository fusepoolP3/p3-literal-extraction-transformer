package eu.fusepool.transformer.literalextraction;

import static eu.fusepool.transformer.literalextraction.LiteralExtractionTransformer.FAM_COUNT;
import static eu.fusepool.transformer.literalextraction.LiteralExtractionTransformer.FAM_DOCUMENT_SENTIMENT_ANNOTATION;
import static eu.fusepool.transformer.literalextraction.LiteralExtractionTransformer.FAM_KEYWORD;
import static eu.fusepool.transformer.literalextraction.LiteralExtractionTransformer.FAM_KEYWORD_ANNOTATION;
import static eu.fusepool.transformer.literalextraction.LiteralExtractionTransformer.FAM_SENTIMENT;

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
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.impl.TypedLiteralImpl;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.XSD;

import eu.fusepool.p3.transformer.HttpRequestEntity;
import eu.fusepool.p3.transformer.SyncTransformer;
import eu.fusepool.p3.transformer.commons.Entity;
import eu.fusepool.p3.vocab.FAM;

public class DummyTransformer implements SyncTransformer {

    protected static final String ENTITY_SUFFIX = "dummyEntity";
    protected static final String TOPIC_SUFFIX = "dummyTopic";
    protected static final String ENTITY_ANNO_SUFFIX = "entityAnno";
    protected static final String TOPIC_ANNO_SUFFIX = "topicAnno";
    protected static final String NAMED_ENTITY_ANNO_SUFFIX = "NeAnno";
    protected static final String KEYWORD_ANNO_SUFFIX = "keywordAnno";
    protected static final String SENTIMENT_ANNO_SUFFIX = "sentimentAnno";
    
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
        //NOTE: for now we are only creating some FAM triples as expected by the
        //      LiteralExtractionTransformer. One could also use a real example
        //      output of a Transformer supporting FAM.
        
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
        
        UriRef personEnhancement = new UriRef(DEFAULT_BASE_URI + NamedEntityTypeEnum.PERS + NAMED_ENTITY_ANNO_SUFFIX);
        graph.add(new TripleImpl(personEnhancement, RDF.type, FAM.EntityMention));
        graph.add(new TripleImpl(personEnhancement, FAM.entity_mention, new PlainLiteralImpl("Max Mustermann")));
        graph.add(new TripleImpl(personEnhancement, FAM.entity_type, new UriRef("http://schema.org/Person")));

        UriRef orgEnhancement = new UriRef(DEFAULT_BASE_URI + NamedEntityTypeEnum.ORG + NAMED_ENTITY_ANNO_SUFFIX);
        graph.add(new TripleImpl(orgEnhancement, RDF.type, FAM.EntityMention));
        graph.add(new TripleImpl(orgEnhancement, FAM.entity_mention, new PlainLiteralImpl("Audi")));
        graph.add(new TripleImpl(orgEnhancement, FAM.entity_type, new UriRef("http://schema.org/Organization")));

        UriRef placeEnhancement = new UriRef(DEFAULT_BASE_URI + NamedEntityTypeEnum.LOC + NAMED_ENTITY_ANNO_SUFFIX);
        graph.add(new TripleImpl(placeEnhancement, RDF.type, FAM.EntityMention));
        graph.add(new TripleImpl(placeEnhancement, FAM.entity_mention, new PlainLiteralImpl("Linz")));
        graph.add(new TripleImpl(placeEnhancement, FAM.entity_type, new UriRef("http://schema.org/Place")));

        UriRef keywordEnhancement = new UriRef(DEFAULT_BASE_URI + KEYWORD_ANNO_SUFFIX);
        graph.add(new TripleImpl(keywordEnhancement, RDF.type, FAM_KEYWORD_ANNOTATION));
        graph.add(new TripleImpl(keywordEnhancement, FAM_KEYWORD, new PlainLiteralImpl("Bruck an der Donau")));
        graph.add(new TripleImpl(keywordEnhancement, FAM_COUNT, new TypedLiteralImpl("3", XSD.int_)));
        graph.add(new TripleImpl(keywordEnhancement, FAM_COUNT, new TypedLiteralImpl("0.87", XSD.double_)));

        UriRef sentimentEnhancement = new UriRef(DEFAULT_BASE_URI + KEYWORD_ANNO_SUFFIX);
        graph.add(new TripleImpl(sentimentEnhancement, RDF.type, FAM_DOCUMENT_SENTIMENT_ANNOTATION));
        graph.add(new TripleImpl(sentimentEnhancement, FAM_SENTIMENT, new TypedLiteralImpl("0.87", XSD.double_)));
        
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
