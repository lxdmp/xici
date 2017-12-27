package com.lxdmp.proxypool;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;

import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;

public class HttpTerminal
{
	private static final String CHARSET_UTF_8 = "utf-8"; // utf-8字符编码
	private static final String CONTENT_TYPE_FORM_URL = "application/x-www-form-urlencoded";
	private static final String CONTENT_TYPE_JSON_URL = "application/json;charset=utf-8";
	private static final String CONTENT_TYPE_TEXT_HTML = "text/xml";
	private static boolean trace = false;
	
	private PoolingHttpClientConnectionManager pool; // 连接管理器
	private RequestConfig requestConfig; // 请求配置
	private String usrAgent = null;
	
	private Proxy fixedHttpProxy = null, fixedHttpsProxy = null;
	private LinkedList<Proxy> temporaryHttpProxy = new LinkedList<Proxy>();
	private LinkedList<Proxy> temporaryHttpsProxy = new LinkedList<Proxy>();

	public HttpTerminal(int timeout_sec) throws Exception
	{
		// - 初始化连接管理器
		
		// 配置同时支持 HTTP 和 HTPPS
		SSLContextBuilder builder = new SSLContextBuilder();
		builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());

		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
			.register("http", PlainConnectionSocketFactory.getSocketFactory())
			.register("https", sslsf).build();

		this.pool = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		this.pool.setMaxTotal(10);
		this.pool.setDefaultMaxPerRoute(2);
		
		// - 初始化请求配置
		int socketTimeout = timeout_sec*1000;
		int connectTimeout = timeout_sec*1000;
		int connectionRequestTimeout = timeout_sec*1000;
		this.requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(connectionRequestTimeout)
			.setSocketTimeout(socketTimeout)
			.setConnectTimeout(connectTimeout)
			.build();
	}

	private CloseableHttpClient getHttpClient(HttpRequestBase req)
	{
		HttpClientBuilder builder = HttpClients.custom();
		builder.setConnectionManager(this.pool); // 设置连接池管理
		builder.setConnectionManagerShared(true);
		builder.setDefaultRequestConfig(this.requestConfig); // 设置请求配置
		builder.setRetryHandler(new DefaultHttpRequestRetryHandler(0,false));// 设置重试次数
		loadProxy(builder, req.getURI().getScheme());
		return builder.build();
	}

	private void loadProxyImpl(HttpClientBuilder builder, String scheme, Proxy proxy)
	{
		builder.setRoutePlanner(new DefaultProxyRoutePlanner(
			new HttpHost(proxy.getIp(), proxy.getPort(), scheme)
		));
	}

	private void loadSpecificProxy(HttpClientBuilder builder, String scheme,
			Proxy specificFixedProxy, LinkedList<Proxy> specificTemporaryProxy)
	{
		if(!specificTemporaryProxy.isEmpty())
			this.loadProxyImpl(builder, scheme, specificTemporaryProxy.pollFirst());
		else if(specificFixedProxy!=null)
			this.loadProxyImpl(builder, scheme, specificFixedProxy);
	}

	private void loadProxy(HttpClientBuilder builder, String scheme)
	{
		//System.out.println("scheme : "+scheme);
		if(scheme.compareTo("http")==0)
			loadSpecificProxy(builder, scheme, fixedHttpProxy, temporaryHttpProxy);
		else if(scheme.compareTo("https")==0)
			loadSpecificProxy(builder, scheme, fixedHttpsProxy, temporaryHttpsProxy);
	}
	
	public LinkedList<Proxy> getTemporaryProxy(String scheme)
	{
		if(scheme.compareTo("http")==0)
			return temporaryHttpProxy;
		else if(scheme.compareTo("https")==0)
			return temporaryHttpsProxy;
		return null;
	}

	public void addTemporaryProxy(Proxy proxy)
	{
		String scheme = proxy.getScheme();
		if(scheme.compareTo("http")==0)
			temporaryHttpProxy.addLast(proxy);
		else if(scheme.compareTo("https")==0)
			temporaryHttpsProxy.addLast(proxy);
	}

	public void setFixedProxy(Proxy proxy)
	{
		String scheme = proxy.getScheme();
		if(scheme.compareTo("http")==0)
			fixedHttpProxy = proxy;
		else if(scheme.compareTo("https")==0)
			fixedHttpsProxy = proxy;
	}

	public void resetFixedProxy(String scheme)
	{
		if(scheme.compareTo("http")==0)
			fixedHttpProxy = null;
		else if(scheme.compareTo("https")==0)
			fixedHttpsProxy = null;
	}

	public Proxy getFixedProxy(String scheme)
	{
		if(scheme.compareTo("http")==0)
			return fixedHttpProxy;
		else if(scheme.compareTo("https")==0)
			return fixedHttpsProxy;
		return null;
	}

	void setUsrAgent(String agent)
	{
		usrAgent = agent;
	}

	String getUsrAgent()
	{
		return usrAgent;
	}

	private String sendHttpPost(HttpPost httpPost)
	{
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;
		String responseContent = null;
		
		try{
			// 创建默认的httpClient实例.
			httpClient = getHttpClient(httpPost);
			// 配置请求信息
			httpPost.setConfig(requestConfig);
			// 执行请求
			response = httpClient.execute(httpPost);
			// 响应实例
			HttpEntity entity = response.getEntity();
			// 响应头
			Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
			if(trace)
			{
				for(Header header : headers)
					System.out.println(header.getName());
			}
			
			// 响应类型
			if(trace)
				System.out.println(ContentType.getOrDefault(response.getEntity()).getMimeType());
	
			// 响应状态
			if(response.getStatusLine().getStatusCode()!=HttpStatus.SC_OK)
				throw new Exception("HTTP Request is not success, Response code is " + response.getStatusLine().getStatusCode());
			responseContent = EntityUtils.toString(entity, CHARSET_UTF_8);
			EntityUtils.consume(entity);
		}catch(Exception e){
			System.out.println(e.getMessage());
		}finally{
			try{
				if(response != null)
					response.close();
			}catch(IOException e){
				e.printStackTrace();
			}

			try{
				if(httpClient!=null)
					httpClient.close();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		
		return responseContent;
	}

	private String sendHttpGet(HttpGet httpGet)
	{
		CloseableHttpClient httpClient = null;
		CloseableHttpResponse response = null;
		String responseContent = null;
		
		try{
			// 创建默认的httpClient实例.
			httpClient = getHttpClient(httpGet);
			// 配置请求信息
			httpGet.setConfig(requestConfig);
			// 执行请求
			response = httpClient.execute(httpGet);
			// 得到响应实例
			HttpEntity entity = response.getEntity();
			// 响应头
			Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
			if(trace)
			{
				for(Header header : headers)
					System.out.println(header.getName());
			}
	
			// 响应类型
			if(trace)
				System.out.println(ContentType.getOrDefault(response.getEntity()).getMimeType());
			
			// 响应状态
			if(response.getStatusLine().getStatusCode()!=HttpStatus.SC_OK)
				throw new Exception("HTTP Request is not success, Response code is " + response.getStatusLine().getStatusCode());
		
			responseContent = EntityUtils.toString(entity, CHARSET_UTF_8);
			EntityUtils.consume(entity);
		}catch(Exception e){
			System.out.println(e.getMessage());
		} finally {
			try{
				if(response!=null)
					response.close();
			}catch(IOException e){
				e.printStackTrace();
			}

			try{
				if(httpClient!=null)
					httpClient.close();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		
		return responseContent;
	}

	public String sendHttpPost(String httpUrl)
	{
		HttpPost httpPost = new HttpPost(httpUrl);
		if(usrAgent!=null)
			httpPost.setHeader("User-Agent", usrAgent);
		return sendHttpPost(httpPost);
	}
	
	public String sendHttpGet(String httpUrl)
	{
		HttpGet httpGet = new HttpGet(httpUrl);
		if(usrAgent!=null)
			httpGet.setHeader("User-Agent", usrAgent);
		return sendHttpGet(httpGet);
	}

	// post提交表单与文件
	public String sendHttpPost(String httpUrl, Map<String, String> maps, List<File> fileLists)
	{
		HttpPost httpPost = new HttpPost(httpUrl);
		MultipartEntityBuilder meBuilder = MultipartEntityBuilder.create();
		if(maps!=null)
		{
			for (String key : maps.keySet())
				meBuilder.addPart(key, new StringBody(maps.get(key), ContentType.TEXT_PLAIN));
		}
		if(fileLists!=null)
		{
			for(File file:fileLists)
			{
				FileBody fileBody = new FileBody(file);
				meBuilder.addPart("files", fileBody);
			}
		}
		HttpEntity reqEntity = meBuilder.build();
		httpPost.setEntity(reqEntity);
		return sendHttpPost(httpPost);
	}
	
	// post提交表单
	public String sendHttpPost(String httpUrl, String params)
	{
		HttpPost httpPost = new HttpPost(httpUrl);
		if(params!=null && params.trim().length()>0)
		{
			StringEntity stringEntity = new StringEntity(params, "UTF-8");
			stringEntity.setContentType(CONTENT_TYPE_FORM_URL);
			httpPost.setEntity(stringEntity);
		}
		return sendHttpPost(httpPost);
	}
	
	// post提交表单
	public String sendHttpPost(String httpUrl, Map<String, String> maps)
	{
		String param = convertStringParamter(maps);
		return sendHttpPost(httpUrl, param);
	}
	
	// post提交json数据
	public String sendHttpPostJson(String httpUrl, String paramsJson)
	{
		HttpPost httpPost = new HttpPost(httpUrl);
		if(paramsJson!=null && paramsJson.trim().length()>0)
		{
			StringEntity stringEntity = new StringEntity(paramsJson, "UTF-8");
			stringEntity.setContentType(CONTENT_TYPE_JSON_URL);
																																		                httpPost.setEntity(stringEntity);
		}
		return sendHttpPost(httpPost);
	}
	
	// post提交xml
	public String sendHttpPostXml(String httpUrl, String paramsXml)
	{
		HttpPost httpPost = new HttpPost(httpUrl);
		if(paramsXml!=null && paramsXml.trim().length()>0)
		{
			StringEntity stringEntity = new StringEntity(paramsXml, "UTF-8");
			stringEntity.setContentType(CONTENT_TYPE_TEXT_HTML);
			httpPost.setEntity(stringEntity);
		}
		return sendHttpPost(httpPost);
	}
	
	private String convertStringParamter(Map parameterMap)
	{
		StringBuffer parameterBuffer = new StringBuffer();
		if(parameterMap!=null)
		{
			Iterator iterator = parameterMap.keySet().iterator();
			String key = null;
			String value = null;
			while (iterator.hasNext())
			{
				key = (String) iterator.next();
				if(parameterMap.get(key)!=null)
					value = (String) parameterMap.get(key);
				else
					value = "";
				parameterBuffer.append(key).append("=").append(value);
				if(iterator.hasNext())
					parameterBuffer.append("&");
			}
		}
		return parameterBuffer.toString();
	}
}

