package com.scienjus.smartqq.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.scienjus.smartqq.constant.ApiURL;
import com.scienjus.smartqq.model.Group;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"Duplicates", "unused"})
public class SmartQQAction {
	private static final Logger log = Logger.getLogger(SmartQQAction.class);

	private static final long Client_ID = 53999199;

	private HttpClient client = HttpClients.createDefault();
	private String ptwebqq;
	private String vfwebqq;
	private String psessionid;
	private long uin;

	public void login() {
		getQRCode();
		String url = verifyQRCode();
		getPtwebqq(url);
		getVfwebqq();
		getUinAndPsessionid();
	}

	private void getQRCode() {

		log.info("开始获取二维码");
		//发送请求的客户端

		String filePath = getClass().getResource("/").getPath().concat("qrcode.png");
		HttpGet get = defaultHttpGet(ApiURL.GET_QE_CODE);
		try (FileOutputStream out = new FileOutputStream(filePath)) {
			HttpResponse response = client.execute(get);
			out.write(EntityUtils.toByteArray(response.getEntity()));
			log.info("二维码已保存在 " + filePath + " 文件中，请打开手机QQ并扫描二维码");
		} catch (IOException e) {
			log.error("获取二维码失败");
		}
	}

	private String verifyQRCode() {
		log.info("等待扫描二维码");
		HttpGet get = defaultHttpGet(ApiURL.VERIFY_QR_CODE);
		get.addHeader("Connection", "Keep-Alive");

		//阻塞直到确认二维码认证成功
		while (true) {
			//todo client.execute会阻塞
			try {
				Thread.sleep(1000);
				HttpResponse response = client.execute(get);
				String responseText = EntityUtils.toString(response.getEntity());

				log.info(responseText);

				if (responseText.contains("成功")) {
					for (String content : responseText.split("','")) {
						if (content.startsWith("http")) {
							return content;
						}
					}
				} else if (responseText.contains("已失效")) {
					log.info("二维码已失效，尝试重新获取二维码");
					getQRCode();
				}
			} catch (IOException e) {
				log.error("校验二维码失败");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void getPtwebqq(String url) {
		log.info("开始获取ptwebqq");
		//发送请求的客户端
		HttpGet get = defaultHttpGet(ApiURL.GET_PTWEBQQ, url);
		try {
			HttpClientContext context = HttpClientContext.create();
			HttpResponse execute = client.execute(get, context);

			log.info(EntityUtils.toString(execute.getEntity()));

			context.getCookieStore().getCookies().stream()
					.filter(cookie -> cookie.getName().equals("ptwebqq"))
					.forEach(cookie -> {
						ptwebqq = cookie.getValue();
						System.out.println("ptwebqq = " + ptwebqq);
					});
		} catch (IOException e) {
			log.error("获取ptwebqq失败");
		}
	}

	private void getVfwebqq() {
		log.info("开始获取vfwebqq");
		//发送请求的客户端

		HttpGet get = defaultHttpGet(ApiURL.GET_VFWEBQQ, ptwebqq);
		try {
			HttpResponse response = client.execute(get);
			String s = EntityUtils.toString(response.getEntity());

			log.info(s);

			JSONObject responseJson = JSON.parseObject(s);
			this.vfwebqq = responseJson.getJSONObject("result").getString("vfwebqq");
		} catch (IOException e) {
			log.error("获取vfwebqq失败");
		}
	}

	private void getUinAndPsessionid() {
		log.info("开始获取uin和psessionid");
		JSONObject r = new JSONObject();
		r.put("ptwebqq", ptwebqq);
		r.put("clientid", Client_ID);
		r.put("psessionid", "");
		r.put("status", "online");

		HttpPost post = defaultHttpPost(ApiURL.GET_UIN_AND_PSESSIONID, new BasicNameValuePair("r", r.toJSONString()));
		try {
			HttpResponse response = client.execute(post);
			String s = EntityUtils.toString(response.getEntity());

			log.info(s);

			JSONObject responseJson = JSON.parseObject(s);
			this.psessionid = responseJson.getJSONObject("result").getString("psessionid");
			this.uin = responseJson.getJSONObject("result").getLongValue("uin");
		} catch (IOException e) {
			log.error("获取uin和psessionid失败");
		}
	}

	public List<Group> getGroupList() {
		log.info("开始获取群列表");
		JSONObject r = new JSONObject();
		r.put("vfwebqq", vfwebqq);
		r.put("hash", hash());
		try {
			//发送请求的客户端
			HttpPost post = defaultHttpPost(ApiURL.GET_GROUP_LIST, new BasicNameValuePair("r", r.toJSONString()));
			post.setEntity(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("r", r.toJSONString()))));
			try {
				HttpResponse response = client.execute(post);
				String s = EntityUtils.toString(response.getEntity());

				log.info(s);

				JSONObject responseJson = JSON.parseObject(s);
				return JSON.parseArray(responseJson.getJSONObject("result").getJSONArray("gnamelist").toJSONString(), Group.class);
			} catch (IOException e) {
				log.error("获取群列表失败");
			}
		} catch (UnsupportedEncodingException e) {
			log.error("获取群列表失败");
		}
		return Collections.emptyList();
	}


	private String hash() {
		return hash(uin, ptwebqq);
	}

	private static String hash(long x, String K) {
		int[] N = new int[4];
		for (int T = 0; T < K.length(); T++) {
			N[T % 4] ^= K.charAt(T);
		}
		String[] U = {"EC", "OK"};
		long[] V = new long[4];
		V[0] = x >> 24 & 255 ^ U[0].charAt(0);
		V[1] = x >> 16 & 255 ^ U[0].charAt(1);
		V[2] = x >> 8 & 255 ^ U[1].charAt(0);
		V[3] = x & 255 ^ U[1].charAt(1);

		long[] U1 = new long[8];

		for (int T = 0; T < 8; T++) {
			U1[T] = T % 2 == 0 ? N[T >> 1] : V[T >> 1];
		}

		String[] N1 = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
		String V1 = "";
		for (long aU1 : U1) {
			V1 += N1[(int) ((aU1 >> 4) & 15)];
			V1 += N1[(int) (aU1 & 15)];
		}
		return V1;
	}

	private static HttpGet defaultHttpGet(ApiURL apiUrl, Object... params) {
		String url = apiUrl.buildUrl(params);
		HttpGet get = new HttpGet(url);
		if (apiUrl.getReferer() != null) {
			get.setHeader("Referer", apiUrl.getReferer());
		}
		get.setHeader("User-Agent", ApiURL.getUserAgent());
		return get;
	}

	private static HttpPost defaultHttpPost(ApiURL apiUrl, BasicNameValuePair... params) {
		HttpPost post = new HttpPost(apiUrl.getUrl());
		if (apiUrl.getReferer() != null) {
			post.setHeader("Referer", apiUrl.getReferer());
		}
		post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), Charset.forName("UTF-8")));
		post.setHeader("Origin", apiUrl.getOrigin());
		post.setHeader("User-Agent", ApiURL.getUserAgent());
		return post;
	}
}
