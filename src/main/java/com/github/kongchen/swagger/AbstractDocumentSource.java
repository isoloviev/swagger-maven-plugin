package com.github.kongchen.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.wordnik.swagger.jaxrs.listing.SwaggerSerializers;
import com.wordnik.swagger.models.Operation;
import com.wordnik.swagger.models.Path;
import com.wordnik.swagger.models.Response;
import com.wordnik.swagger.models.Swagger;
import com.wordnik.swagger.util.Json;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 *
 * @author: chekong 05/13/2013
 */
public abstract class AbstractDocumentSource {
	protected final LogAdapter LOG;

	private final String outputPath;

	private final String templatePath;

	private final String swaggerPath;

	private final String overridingModels;

	protected Swagger swagger;

	private ObjectMapper mapper = new ObjectMapper();

	public AbstractDocumentSource(LogAdapter logAdapter, String outputPath,
								  String outputTpl, String swaggerOutput, String overridingModels) {
		LOG = logAdapter;
		this.outputPath = outputPath;
		this.templatePath = outputTpl;
		this.swaggerPath = swaggerOutput;
		this.overridingModels = overridingModels;
	}

	public abstract void loadDocuments() throws Exception, GenerateException, GenerateException;

	public void toSwaggerDocuments(String swaggerUIDocBasePath)
			throws GenerateException {
		if (swaggerPath == null) {
			return;
		}
		File dir = new File(swaggerPath);
		if (dir.isFile()) {
			throw new GenerateException(String.format(
					"Swagger-outputDirectory[%s] must be a directory!",
					swaggerPath));
		}

		if (!dir.exists()) {
			try {
				FileUtils.forceMkdir(dir);
			} catch (IOException e) {
				throw new GenerateException(String.format(
						"Create Swagger-outputDirectory[%s] failed.",
						swaggerPath));
			}
		}
		cleanupOlds(dir);

		File swaggerFile = new File(dir, "swagger.json");
		try {
			Json.pretty().writeValue(swaggerFile, swagger);
		} catch (IOException e) {
			throw new GenerateException(e);
		}
	}

	public void loadOverridingModels() throws GenerateException {
		if (overridingModels != null) {
			try {
				JsonNode readTree = mapper.readTree(this.getClass()
						.getResourceAsStream(overridingModels));
				for (JsonNode jsonNode : readTree) {
					JsonNode classNameNode = jsonNode.get("className");
					String className = classNameNode.asText();
					JsonNode jsonStringNode = jsonNode.get("jsonString");
					String jsonString = jsonStringNode.asText();


					// 1.5.0 does not support override models by now
				}
			} catch (JsonProcessingException e) {
				throw new GenerateException(
						String.format(
								"Swagger-overridingModels[%s] must be a valid JSON file!",
								overridingModels), e);
			} catch (IOException e) {
				throw new GenerateException(String.format(
						"Swagger-overridingModels[%s] not found!",
						overridingModels), e);
			}
		}
	}

	private void cleanupOlds(File dir) {
		if (dir.listFiles() != null) {
			for (File f : dir.listFiles()) {
				if (f.getName().endsWith("json")) {
					f.delete();
				}
			}
		}
	}

	private void writeInDirectory(File dir, Swagger swaggerDoc,
			String basePath) throws GenerateException {

//		try {
//			File serviceFile = createFile(dir, filename);
//			String json = JsonSerializer.asJson(swaggerDoc);
//			JsonNode tree = mapper.readTree(json);
//			if (basePath != null) {
//				((ObjectNode) tree).put("basePath", basePath);
//			}
//
//			JsonUtil.mapper().writerWithDefaultPrettyPrinter()
//					.writeValue(serviceFile, tree);
//		} catch (IOException e) {
//			throw new GenerateException(e);
//		}
	}

	protected File createFile(File dir, String outputResourcePath)
			throws IOException {
		File serviceFile;
		int i = outputResourcePath.lastIndexOf("/");
		if (i != -1) {
			String fileName = outputResourcePath.substring(i + 1);
			String subDir = outputResourcePath.substring(0, i);
			File finalDirectory = new File(dir, subDir);
			finalDirectory.mkdirs();
			serviceFile = new File(finalDirectory, fileName);
		} else {
			serviceFile = new File(dir, outputResourcePath);
		}
		while (!serviceFile.createNewFile()) {
			serviceFile.delete();
		}
		LOG.info("Creating file " + serviceFile.getAbsolutePath());
		return serviceFile;
	}

	public void toDocuments() throws GenerateException {

		LOG.info("Writing doc to " + outputPath + "...");

		FileOutputStream fileOutputStream;
		try {
			fileOutputStream = new FileOutputStream(outputPath);
		} catch (FileNotFoundException e) {
			throw new GenerateException(e);
		}
		OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream,
				Charset.forName("UTF-8"));

		try {
			TemplatePath tp = Utils.parseTemplateUrl(templatePath);

			Handlebars handlebars = new Handlebars(tp.loader);
			initHandlebars(handlebars);

			Template template = handlebars.compile(tp.name);

			template.apply(swagger, writer);
			writer.close();
			LOG.info("Done!");
		} catch (MalformedURLException e) {
			throw new GenerateException(e);
		} catch (IOException e) {
			throw new GenerateException(e);
		}
	}

	private void initHandlebars(Handlebars handlebars) {
		handlebars.registerHelper("ifeq", new Helper<String>() {
			@Override
			public CharSequence apply(String value, Options options) throws IOException {
				if (value == null || options.param(0) == null) return options.inverse();
				if (value.equals(options.param(0))){
					return options.fn();
				}
				return options.inverse();
			}
		});

		handlebars.registerHelper("basename", new Helper<String>() {
			@Override
			public CharSequence apply(String value, Options options) throws IOException {
				if (value == null) return null;
				int lastSlash = value.lastIndexOf("/");
				if (lastSlash == -1) {
					return value;
				} else {
					return value.substring(lastSlash + 1);
				}
			}
		});

		handlebars.registerHelper(StringHelpers.join.name(), StringHelpers.join);
		handlebars.registerHelper(StringHelpers.lower.name(), StringHelpers.lower);
	}

	private String getUrlParent(URL url) {

		if (url == null) return null;

		String strurl = url.toString();
		int idx = strurl.lastIndexOf('/');
		if (idx == -1) {
			return strurl;
		}
		return strurl.substring(0,idx);
	}

	public void reorderApis() throws GenerateException {
		TreeMap<String, Path> sortedMap = new TreeMap<String, Path>(new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return o1.compareTo(o2);
			}
		});
		sortedMap.putAll(swagger.getPaths());
		swagger.paths(sortedMap);

		for(Path path : swagger.getPaths().values()) {
			String methods[] = {"Get", "Delete", "Post", "Put", "Options", "Patch"};
			for (String m : methods) {
				reorderResponses(path, m);
			}
		}

	}


	private void reorderResponses(Path path, String method) throws GenerateException {
		try {
			Method m = Path.class.getDeclaredMethod("get" + method);
			Operation op = (Operation) m.invoke(path);
			if (op == null) return;
			Map<String, Response> responses = op.getResponses();
			TreeMap<String, Response> res = new TreeMap<String, Response>();
			res.putAll(responses);
			op.setResponses(res);
		} catch (NoSuchMethodException e) {
			throw new GenerateException(e);
		} catch (InvocationTargetException e) {
			throw new GenerateException(e);
		} catch (IllegalAccessException e) {
			throw new GenerateException(e);
		}
	}
}


class TemplatePath {
	String prefix;
	String name;
	String suffix;
	public TemplateLoader loader;
}