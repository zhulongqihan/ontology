package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.ontology.OntologyTypeDefinition;

import java.util.List;
import java.util.Map;

interface OntologyProvider {
    Map<String, Object> assemble(String modelId, String contextId, Map<String, Object> values,
                                 Object input, List<OntologyTypeDefinition> definitions);
}
