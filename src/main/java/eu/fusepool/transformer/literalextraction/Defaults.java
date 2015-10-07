package eu.fusepool.transformer.literalextraction;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.ontologies.FOAF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.ontologies.SKOS04;

import eu.fusepool.p3.vocab.FAM;

public class Defaults {

    public static final int DEFAULT_MIN_LITERAL_LENGTH = 50;

    private Defaults(){/* no instances allowed*/}
    
    private static final String NS_FAM = FAM.THIS_ONTOLOGY.getUnicodeString();
    
    public static final UriRef DEFAULT_ENTITY_PREDICATE = FAM.entity_reference;
    public static final UriRef DEFAULT_TOPIC_PREDICATE = FAM.topic_reference;
    
    public static final Map<NamedEntityTypeEnum,UriRef> DEFAULT_NAMED_ENTITY_TYPE_PREDICATES;
    public static final Map<UriRef,NamedEntityTypeEnum> DEFAULT_NAMED_ENTITY_TYPE_MAPPINGS;
    
    public static final UriRef DEFAULT_KEYWORD_PREDICATE = new UriRef(
            FAM.THIS_ONTOLOGY.getUnicodeString() + "keyword");
    public static final UriRef DEFAULT_SENTIMENT_PREDICATE = new UriRef(
            FAM.THIS_ONTOLOGY.getUnicodeString() + "sentiment");
    
    /**
     * Predicate used for Named Entities of the type Person
     */
    public static final UriRef DEFAULT_PERSON_PREDICATE = new UriRef(NS_FAM + "preson-ne-reference");
    /**
     * Predicate used for Named Entities of the type Organization
     */
    public static final UriRef DEFAULT_ORGANIZATION_PREDICATE = new UriRef(NS_FAM + "organization-ne-reference");
    /**
     * Predicate used for Named Entities of the type Location
     */
    public static final UriRef DEFAULT_LOCATION_PREDICATE = new UriRef(NS_FAM + "location-ne-reference");
    /**
     * Predicate used for Named Entities of other types as Person, Organization or Location
     */
    public static final UriRef DEFAULT_OTHER_PREDICATE = new UriRef(NS_FAM + "other-ne-reference");
    /**
     * Predicate used for Named Entities with no type specific mapping
     */
    public static final UriRef DEFAULT_NAMED_ENTITY_PREDICATE = new UriRef(NS_FAM + "named-entity-reference");
    
    static {
        //Provides mappings for DBPedia, NERD, SCHEMA and FOAF
        Map<UriRef,NamedEntityTypeEnum> mappings = new HashMap<>();
        mappings.put(new UriRef("http://dbpedia.org/ontology/Person"), NamedEntityTypeEnum.PERS);
        mappings.put(new UriRef("http://schema.org/Person"), NamedEntityTypeEnum.PERS);
        mappings.put(FOAF.Person, NamedEntityTypeEnum.PERS);
        mappings.put(new UriRef("http://nerd.eurecom.fr/ontology#Person"), NamedEntityTypeEnum.PERS);
        mappings.put(new UriRef("http://dbpedia.org/ontology/Organisation"), NamedEntityTypeEnum.ORG);
        mappings.put(new UriRef("http://schema.org/Organization"), NamedEntityTypeEnum.ORG);
        mappings.put(FOAF.Organization, NamedEntityTypeEnum.ORG);
        mappings.put(new UriRef("http://nerd.eurecom.fr/ontology#Organization"), NamedEntityTypeEnum.ORG);
        mappings.put(new UriRef("http://dbpedia.org/ontology/Place"), NamedEntityTypeEnum.LOC);
        mappings.put(new UriRef("http://schema.org/Place"), NamedEntityTypeEnum.LOC);
        mappings.put(new UriRef("http://www.geonames.org/ontology#Feature"), NamedEntityTypeEnum.LOC);
        mappings.put(new UriRef("http://nerd.eurecom.fr/ontology#Location"), NamedEntityTypeEnum.LOC);
        mappings.put(SKOS04.Concept, NamedEntityTypeEnum.MISC);
        mappings.put(new UriRef("http://schema.org/Intangible"), NamedEntityTypeEnum.MISC);
        mappings.put(null, NamedEntityTypeEnum.UNK);
        DEFAULT_NAMED_ENTITY_TYPE_MAPPINGS = Collections.unmodifiableMap(mappings);

        //Provides the default predicates (can be configured)
        Map<NamedEntityTypeEnum,UriRef> predicates = new EnumMap<>(NamedEntityTypeEnum.class);
        predicates.put(NamedEntityTypeEnum.PERS, DEFAULT_PERSON_PREDICATE);
        predicates.put(NamedEntityTypeEnum.ORG, DEFAULT_ORGANIZATION_PREDICATE);
        predicates.put(NamedEntityTypeEnum.LOC, DEFAULT_LOCATION_PREDICATE);
        predicates.put(NamedEntityTypeEnum.MISC, DEFAULT_OTHER_PREDICATE);
        predicates.put(NamedEntityTypeEnum.UNK, DEFAULT_NAMED_ENTITY_PREDICATE);
        DEFAULT_NAMED_ENTITY_TYPE_PREDICATES = Collections.unmodifiableMap(predicates);
    }
    
    public static final Set<UriRef> DEFAULT_LITERAL_PREDICATES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                     RDFS.comment, SKOS04.note, SKOS04.definition, 
                     new UriRef("http://schema.org/description"))));

    
}
