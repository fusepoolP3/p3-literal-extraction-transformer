package eu.fusepool.transformer.literalextraction;

public enum NamedEntityTypeEnum {

    /**
     * Persons
     */
    PERS,
    /**
     * Organizations
     */
    ORG,
    /**
     * Location, Places ...
     */
    LOC,
    /**
     * Misc. types
     */
    MISC,
    /**
     * Unknown type - if no type is given for the Named Entity
     */
    UNK;
}
