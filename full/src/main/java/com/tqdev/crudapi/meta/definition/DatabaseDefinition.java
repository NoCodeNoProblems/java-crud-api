package com.tqdev.crudapi.meta.definition;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import org.jooq.AlterTableUsingIndexStep;
import org.jooq.Constraint;
import org.jooq.CreateTableConstraintStep;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tqdev.crudapi.meta.reflection.DatabaseReflection;
import com.tqdev.crudapi.meta.reflection.ReflectedTable;

public class DatabaseDefinition {

	public static final Logger logger = LoggerFactory.getLogger(DatabaseDefinition.class);

	protected LinkedHashMap<String, TableDefinition> tables;

	public Collection<TableDefinition> getTables() {
		return tables.values();
	}

	public void setTables(Collection<TableDefinition> tables) {
		this.tables = new LinkedHashMap<>();
		for (TableDefinition table : tables) {
			this.tables.put(table.getName(), table);
		}
	}

	public TableDefinition get(String tableName) {
		return tables.get(tableName);
	}

	public void create(DSLContext dsl) throws DatabaseDefinitionException {
		ArrayList<String> created = new ArrayList<>();
		for (String tableName : tables.keySet()) {
			TableDefinition table = tables.get(tableName);
			ArrayList<Field<?>> fields = table.getFields(dsl);
			ArrayList<Constraint> constraints = table.getPkConstraints(dsl, tableName);
			CreateTableConstraintStep query = dsl.createTable(DSL.name(tableName)).columns(fields)
					.constraints(constraints);
			logger.info("Executing SQL: " + query.getSQL());
			query.execute();
			created.add(tableName);
		}
		for (String tableName : created) {
			TableDefinition table = tables.get(tableName);
			for (Constraint constraint : table.getFkConstraints(dsl, tableName, this)) {
				AlterTableUsingIndexStep query = dsl.alterTable(DSL.name(tableName)).add(constraint);
				logger.info("Executing SQL: " + query.getSQL());
				query.execute();
			}
		}
	}

	public DatabaseDefinition() {
		tables = new LinkedHashMap<>();
	}

	public DatabaseDefinition(DatabaseReflection database) {
		tables = new LinkedHashMap<>();
		for (String tableName : database.tableNames()) {
			ReflectedTable table = database.get(tableName);
			tables.put(tableName, new TableDefinition(table));
		}
	}

	public static DatabaseDefinition fromFile(String filename)
			throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		ClassPathResource resource = new ClassPathResource(filename);
		DatabaseDefinition result;
		try {
			result = mapper.readValue(resource.getInputStream(), DatabaseDefinition.class);
		} catch (FileNotFoundException e) {
			result = new DatabaseDefinition();
		}
		return result;
	}

}
