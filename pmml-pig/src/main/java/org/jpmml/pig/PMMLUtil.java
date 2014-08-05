/*
 * Copyright (c) 2014 Villu Ruusmann
 *
 * This file is part of JPMML-Pig
 *
 * JPMML-Pig is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Pig is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Pig.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.pig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.logicalLayer.schema.Schema.FieldSchema;
import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.ModelEvaluatorFactory;
import org.jpmml.manager.PMMLManager;
import org.jpmml.model.ImportFilter;
import org.jpmml.model.JAXBUtil;
import org.xml.sax.InputSource;

public class PMMLUtil {

	private PMMLUtil(){
	}

	static
	public Object evaluateSimple(Class<?> clazz, Tuple tuple) throws IOException {

		if(tuple == null){
			return null;
		}

		Evaluator evaluator = getEvaluator(clazz);

		Map<FieldName, FieldValue> arguments = loadArguments(evaluator, tuple);

		Map<FieldName, ?> result = evaluator.evaluate(arguments);

		Object targetValue = result.get(evaluator.getTargetField());

		return EvaluatorUtil.decode(targetValue);
	}

	static
	public Tuple evaluateComplex(Class<?> clazz, Tuple tuple) throws IOException {

		if(tuple == null){
			return null;
		}

		Evaluator evaluator = getEvaluator(clazz);

		Map<FieldName, FieldValue> arguments = loadArguments(evaluator, tuple);

		Map<FieldName, ?> result = evaluator.evaluate(arguments);

		return storeResult(evaluator, result);
	}

	static
	public Schema getResultType(Class<?> clazz){
		Evaluator evaluator;

		try {
			evaluator = getEvaluator(clazz);
		} catch(IOException ioe){
			return null;
		}

		Schema tuple = new Schema();

		List<FieldName> targetFields = evaluator.getTargetFields();
		for(FieldName targetField : targetFields){
			DataField field = evaluator.getDataField(targetField);

			tuple.add(new FieldSchema(targetField.getValue(), getDataType(field.getDataType())));
		}

		List<FieldName> outputFields = evaluator.getOutputFields();
		for(FieldName outputField : outputFields){
			OutputField field = evaluator.getOutputField(outputField);

			tuple.add(new FieldSchema(outputField.getValue(), getDataType(field.getDataType())));
		}

		Schema result = new Schema();
		result.add(new FieldSchema((String)null, tuple));

		return result;
	}

	static
	private Map<FieldName, FieldValue> loadArguments(Evaluator evaluator, Tuple tuple) throws ExecException {

		if(tuple.size() == 1){
			byte type = tuple.getType(0);

			switch(type){
				case DataType.TUPLE:
					return loadPrimitiveList(evaluator, (Tuple)tuple.get(0));
				default:
					break;
			}
		}

		return loadPrimitiveList(evaluator, tuple);
	}

	static
	private Map<FieldName, FieldValue> loadPrimitiveList(Evaluator evaluator, Tuple tuple) throws ExecException {
		Map<FieldName, FieldValue> result = Maps.newLinkedHashMap();

		List<FieldName> activeFields = evaluator.getActiveFields();
		if(activeFields.size() != tuple.size()){
			throw new ExecException();
		}

		int i = 0;

		for(FieldName activeField : activeFields){
			Object object = tuple.get(i);

			FieldValue value = EvaluatorUtil.prepare(evaluator, activeField, object);

			result.put(activeField, value);

			i++;
		}

		return result;
	}

	static
	private Tuple storeResult(Evaluator evaluator, Map<FieldName, ?> result){
		List<Object> values = Lists.newArrayList();

		List<FieldName> targetFields = evaluator.getTargetFields();
		for(FieldName targetField : targetFields){
			values.add(EvaluatorUtil.decode(result.get(targetField)));
		}

		List<FieldName> outputFields = evaluator.getOutputFields();
		for(FieldName outputField : outputFields){
			values.add(result.get(outputField));
		}

		TupleFactory tupleFactory = TupleFactory.getInstance();

		return tupleFactory.newTuple(values);
	}

	static
	private byte getDataType(org.dmg.pmml.DataType dataType){

		switch(dataType){
			case STRING:
				return DataType.CHARARRAY;
			case INTEGER:
				return DataType.INTEGER;
			case FLOAT:
				return DataType.FLOAT;
			case DOUBLE:
				return DataType.DOUBLE;
			case BOOLEAN:
				return DataType.BOOLEAN;
			default:
				return DataType.ERROR;
		}
	}

	static
	private Evaluator getEvaluator(Class<?> clazz) throws IOException {

		try {
			return PMMLUtil.evaluatorCache.get(clazz);
		} catch(Exception e){
			throw new IOException(e);
		}
	}

	static
	private PMML loadPMML(InputStream is) throws Exception {
		Source source = ImportFilter.apply(new InputSource(is));

		return JAXBUtil.unmarshalPMML(source);
	}

	static
	private ModelEvaluator<?> loadEvaluator(Class<?> clazz) throws Exception {
		String path = clazz.getSimpleName() + ".pmml";

		InputStream is = clazz.getResourceAsStream(path);
		if(is == null){
			throw new FileNotFoundException(path);
		}

		PMML pmml;

		try {
			pmml = loadPMML(is);
		} finally {
			is.close();
		}

		PMMLManager pmmlManager = new PMMLManager(pmml);

		return (ModelEvaluator<?>)pmmlManager.getModelManager(null, ModelEvaluatorFactory.getInstance());
	}

	private static final LoadingCache<Class<?>, Evaluator> evaluatorCache = CacheBuilder.newBuilder()
		.weakKeys()
		.build(new CacheLoader<Class<?>, Evaluator>(){

			@Override
			public Evaluator load(Class<?> clazz) throws Exception {
				return loadEvaluator(clazz);
			}
		});
}