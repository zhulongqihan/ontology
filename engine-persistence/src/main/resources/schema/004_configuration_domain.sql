CREATE TABLE IF NOT EXISTS engine_model (
    model_id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    schema_version INTEGER NOT NULL,
    workflow_version INTEGER NOT NULL,
    initial_state TEXT NOT NULL,
    unknown_field_policy TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS schema_definition (
    model_id TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    published_at TEXT NOT NULL,
    PRIMARY KEY(model_id, schema_version),
    FOREIGN KEY(model_id) REFERENCES engine_model(model_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schema_field (
    model_id TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    field_name TEXT NOT NULL,
    field_type TEXT NOT NULL,
    required INTEGER NOT NULL,
    field_version INTEGER NOT NULL,
    default_value_json TEXT,
    updated_at TEXT NOT NULL,
    PRIMARY KEY(model_id, schema_version, field_name),
    FOREIGN KEY(model_id, schema_version) REFERENCES schema_definition(model_id, schema_version) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_schema_field_model ON schema_field(model_id, schema_version);

CREATE TABLE IF NOT EXISTS workflow_definition (
    model_id TEXT NOT NULL,
    workflow_version INTEGER NOT NULL,
    initial_state TEXT NOT NULL,
    published_at TEXT NOT NULL,
    PRIMARY KEY(model_id, workflow_version),
    FOREIGN KEY(model_id) REFERENCES engine_model(model_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workflow_transition (
    model_id TEXT NOT NULL,
    workflow_version INTEGER NOT NULL,
    from_state TEXT NOT NULL,
    event TEXT NOT NULL,
    to_state TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY(model_id, workflow_version, from_state, event),
    FOREIGN KEY(model_id, workflow_version) REFERENCES workflow_definition(model_id, workflow_version) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workflow_transition_model ON workflow_transition(model_id, workflow_version);

CREATE TABLE IF NOT EXISTS ontology_type (
    ontology_type_id TEXT NOT NULL PRIMARY KEY,
    label TEXT NOT NULL,
    description TEXT,
    type_version INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ontology_attribute (
    ontology_type_id TEXT NOT NULL,
    attribute_name TEXT NOT NULL,
    attribute_kind TEXT NOT NULL,
    type_version INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY(ontology_type_id, attribute_name, attribute_kind),
    FOREIGN KEY(ontology_type_id) REFERENCES ontology_type(ontology_type_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ontology_relation (
    ontology_type_id TEXT NOT NULL,
    relation_name TEXT NOT NULL,
    target_type TEXT NOT NULL,
    cardinality TEXT NOT NULL,
    type_version INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY(ontology_type_id, relation_name),
    FOREIGN KEY(ontology_type_id) REFERENCES ontology_type(ontology_type_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_registration (
    service_id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    provider TEXT NOT NULL,
    status TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    version TEXT NOT NULL,
    registered_at TEXT NOT NULL
);
