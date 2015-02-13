package eu.fusepool.transformer.literalextraction;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.LockableMGraph;
import org.apache.clerezza.rdf.core.access.LockableMGraphWrapper;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;

import eu.fusepool.p3.transformer.client.Transformer;

class LiteralExtractonJob implements Serializable {

    private static final long serialVersionUID = 3842095678526255415L;

    protected final String requestId;

    protected final Transformer transformer;

    private final Set<UriRef> literalPredicates;

    protected final TripleCollection dataset;
    protected final LockableMGraph results;
    
    protected UriRef referencedEntityPredicate;
    protected UriRef assigendTopicPredicate;

    private Set<String> activeLanguages;
    
    LiteralExtractonJob(String requestId, Transformer transformer, MGraph graph) {
        this(requestId,transformer,graph,graph,null);
    }
    LiteralExtractonJob(String requestId, Transformer transformer, MGraph graph, Set<UriRef> literalPredicates) {
        this(requestId,transformer,graph,graph, literalPredicates);
    }
    
    LiteralExtractonJob(String requestId, Transformer transformer, TripleCollection dataset, MGraph results) {
        this(requestId,transformer,dataset,results,null);
    }
    LiteralExtractonJob(String requestId, Transformer transformer, TripleCollection dataset, MGraph results,
            Set<UriRef> literalPredicates) {
        assert requestId != null;
        this.requestId = requestId;
        assert transformer != null;
        this.transformer = transformer;
        assert dataset != null;
        this.dataset = dataset;
        this.results = results instanceof LockableMGraph ? (LockableMGraph)results : 
            new LockableMGraphWrapper(results == null ? new SimpleMGraph() : results);
        assert literalPredicates == null || (!literalPredicates.isEmpty() &&
                !literalPredicates.contains(null));
        this.literalPredicates = Collections.unmodifiableSet(
                literalPredicates == null ? Defaults.DEFAULT_LITERAL_PREDICATES : literalPredicates);
    }

    public UriRef getReferencedEntityPredicate() {
        return referencedEntityPredicate == null ? Defaults.DEFAULT_REFERENCED_ENTITY_PREDICATE :
            referencedEntityPredicate;
    }

    public void setReferencedEntityPredicate(UriRef referencedEntityPredicate) {
        this.referencedEntityPredicate = referencedEntityPredicate;
    }

    public UriRef getAssigendTopicPredicate() {
        return assigendTopicPredicate == null ? Defaults.DEFAULT_ASSIGNED_TOPIC_REFERENCE :
            assigendTopicPredicate;
    }

    public void setAssigendTopicPredicate(UriRef assigendTopicPredicate) {
        this.assigendTopicPredicate = assigendTopicPredicate;
    }

    public Transformer getTransformer() {
        return transformer;
    }
    
    

    public TripleCollection getDataset() {
        return dataset;
    }

    public LockableMGraph getResults() {
        return results;
    }

    public Set<UriRef> getLiteralPredicates() {
        return literalPredicates;
    }
    @Override
    public int hashCode() {
        return requestId.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return requestId.equals(((LiteralExtractonJob)obj).requestId);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append("[id: ")
                .append(requestId).append(']').toString();
    }
    /**
     * Sets the active languages. If the parsed set is <code>null</code>, is
     * empty or contains the <code>null</code> value all languages will be set
     * active. Otherwise only the set of contained languages will be processed.
     * Parsed languages are trimmed.
     * @param activeLanguages the set of active languages
     */
    public void setActiveLanguages(Collection<String> activeLanguages) {
        if(activeLanguages == null || activeLanguages.isEmpty()
                || activeLanguages.contains(null)){
            this.activeLanguages = null;
        } else {
            Set<String> langs = new HashSet<>();
            for(String lang : activeLanguages){
                //convert to lower case as language codes are case insensitive
                langs.add(lang.trim().toLowerCase(Locale.ROOT));
            }
            this.activeLanguages = Collections.unmodifiableSet(langs);
        }
    }
    /**
     * Checks if the parsed language is active for this job.
     * @param language the language to check. <code>null</code> language will 
     * always be accepted (interpreted as language unknown - so it might be
     * one of the accepted).
     * @return if the language is active (should be processed)
     */
    public boolean isActiveLanguage(Language language){
        if(activeLanguages == null || language == null){
            return true; //all allowed
        } else {
            return activeLanguages.contains(language.toString().trim().toLowerCase(Locale.ROOT));
        }
    }
    /**
     * Getter for the read-only set of active languages
     * @return the active languages or <code>null</code> if all are active
     */
    public Set<String> getActiveLanguages() {
        return activeLanguages;
    }
}
