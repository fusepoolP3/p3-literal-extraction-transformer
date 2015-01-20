package eu.fusepool.transformer.literalextraction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.ontologies.SKOS04;

import eu.fusepool.p3.vocab.FAM;

public interface Defaults {

    UriRef DEFAULT_REFERENCED_ENTITY_PREDICATE = FAM.entity_reference;
    UriRef DEFAULT_ASSIGNED_TOPIC_REFERENCE = FAM.topic_reference;
    
    Set<UriRef> DEFAULT_LITERAL_PREDICATES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                     RDFS.comment, SKOS04.note, SKOS04.definition, 
                     new UriRef("http://schema.org/description"))));

    
}
