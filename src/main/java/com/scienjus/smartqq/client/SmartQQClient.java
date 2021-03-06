package com.scienjus.smartqq.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.scienjus.smartqq.callback.MessageCallback;
import com.scienjus.smartqq.constant.ApiURL;
import com.scienjus.smartqq.model.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Api客户端
 *
 * @author ScienJus
 * @date 2015/12/18.
 */
@SuppressWarnings("Duplicates")
public class SmartQQClient {

	//日志
	private static final Logger LOGGER = Logger.getLogger(SmartQQClient.class);

	//执行拉取消息的线程池
	private static final ScheduledThreadPoolExecutor POOL = new ScheduledThreadPoolExecutor(20);

	//消息id，这个好像可以随便设置，所以设成全局的
	private static long MESSAGE_ID = 43690001;

	//客户端id，固定的
	private static final long Client_ID = 53999199;

	//保存cookie信息
	private HttpClientContext context = HttpClientContext.create();

	//鉴权参数
	private String ptwebqq;

	private String vfwebqq;

	private long uin;

	private String psessionid;

	//拉取消息的线程
	private ScheduledFuture pollMessageFuture;

	/**
	 * 登录
	 */
	public void login() {
		getQRCode();
		String url = verifyQRCode();
		getPtwebqq(url);
		getVfwebqq();
		getUinAndPsessionid();
	}

	//登录流程1：获取二维码
	private void getQRCode() {
		LOGGER.info("开始获取二维码");
		//发送请求的客户端
		CloseableHttpClient client = HttpClients.createDefault();
		String filePath = getClass().getResource("/").getPath().concat("qrcode.png");
		HttpGet get = defaultHttpGet(ApiURL.GET_QE_CODE);
		try (CloseableHttpResponse response = client.execute(get, context);
			 FileOutputStream out = new FileOutputStream(filePath)) {
			int len;
			byte[] buffer = new byte[1024];
			while ((len = response.getEntity().getContent().read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
			out.close();
			LOGGER.info("二维码已保存在 " + filePath + " 文件中，请打开手机QQ并扫描二维码");
		} catch (IOException e) {
			LOGGER.error("获取二维码失败");
		}
	}

	//登录流程2：校验二维码
	private String verifyQRCode() {
		LOGGER.info("等待扫描二维码");
		HttpGet get = defaultHttpGet(ApiURL.VERIFY_QR_CODE);
		//发送请求的客户端
		CloseableHttpClient client = HttpClients.createDefault();

		//阻塞直到确认二维码认证成功
		while (true) {
			sleep(1);
			try (CloseableHttpResponse response = client.execute(get, context)) {
				String responseText = getResponseText(response);
				if (responseText.contains("成功")) {
					for (String content : responseText.split("','")) {
						if (content.startsWith("http")) {
							return content;
						}
					}
				} else if (responseText.contains("已失效")) {
					LOGGER.info("二维码已失效，尝试重新获取二维码");
					getQRCode();
				}
			} catch (IOException e) {
				LOGGER.error("校验二维码失败");
			}
		}

	}

	//登录流程3：获取ptwebqq
	private void getPtwebqq(String url) {
		LOGGER.info("开始获取ptwebqq");
		//发送请求的客户端
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet get = defaultHttpGet(ApiURL.GET_PTWEBQQ, url);
		try {
			client.execute(get, context);
			for (Cookie cookie : context.getCookieStore().getCookies()) {
				if (cookie.getName().equals("ptwebqq")) {
					this.ptwebqq = cookie.getValue();
				}
			}
		} catch (IOException e) {
			LOGGER.error("获取ptwebqq失败");
		}
	}

	//登录流程4：获取vfwebqq
	private void getVfwebqq() {
		LOGGER.info("开始获取vfwebqq");
		//发送请求的客户端
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet get = defaultHttpGet(ApiURL.GET_VFWEBQQ, ptwebqq);
		try (CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			this.vfwebqq = responseJson.getJSONObject("result").getString("vfwebqq");
		} catch (IOException e) {
			LOGGER.error("获取vfwebqq失败");
		}
	}

	//登录流程5：获取uin和psessionid
	private void getUinAndPsessionid() {
		LOGGER.info("开始获取uin和psessionid");
		JSONObject r = new JSONObject();
		r.put("ptwebqq", ptwebqq);
		r.put("clientid", Client_ID);
		r.put("psessionid", "");
		r.put("status", "online");

		HttpPost post = defaultHttpPost(ApiURL.GET_UIN_AND_PSESSIONID, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			this.psessionid = responseJson.getJSONObject("result").getString("psessionid");
			this.uin = responseJson.getJSONObject("result").getLongValue("uin");
		} catch (IOException e) {
			LOGGER.error("获取uin和psessionid失败");
		}
	}

	/**
	 * 获取群列表
	 *
	 * @return
	 */
	public List<Group> getGroupList() {
		LOGGER.info("开始获取群列表");
		JSONObject r = new JSONObject();
		r.put("vfwebqq", vfwebqq);
		r.put("hash", hash());
		try {
			//发送请求的客户端
			HttpPost post = defaultHttpPost(ApiURL.GET_GROUP_LIST, new BasicNameValuePair("r", r.toJSONString()));
			post.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("r", r.toJSONString()))));
			try (CloseableHttpClient client = HttpClients.createDefault();
				 CloseableHttpResponse response = client.execute(post, context)) {
				JSONObject responseJson = JSON.parseObject(getResponseText(response));
				return JSON.parseArray(responseJson.getJSONObject("result").getJSONArray("gnamelist").toJSONString(), Group.class);
			} catch (IOException e) {
				LOGGER.error("获取群列表失败");
			}
		} catch (UnsupportedEncodingException e) {
			LOGGER.error("获取群列表失败");
		}
		return Collections.emptyList();
	}

	/**
	 * 拉取消息
	 *
	 * @param callback 获取消息后的回调
	 */
	public void pollMessage(MessageCallback callback) {
		LOGGER.info("开始接受消息");
		JSONObject r = new JSONObject();
		r.put("ptwebqq", ptwebqq);
		r.put("clientid", Client_ID);
		r.put("psessionid", psessionid);
		r.put("key", "");

		HttpPost post = defaultHttpPost(ApiURL.POLL_MESSAGE, new BasicNameValuePair("r", r.toJSONString()));
		post.setHeader("Connection","keep-alive");
		if (pollMessageFuture != null) {
			pollMessageFuture.cancel(false);
		}
		pollMessageFuture = POOL.scheduleWithFixedDelay(new PollMessageTask(post, callback), 1, 1, TimeUnit.MILLISECONDS);
	}

	/**
	 * 停止拉取消息
	 */
	public void stopPoll() {
		if (pollMessageFuture != null) {
			pollMessageFuture.cancel(false);
		}
		pollMessageFuture = null;
	}

	/**
	 * 发送群消息
	 *
	 * @param groupId 群id
	 * @param msg     消息内容
	 */
	public void sendMessageToGroup(long groupId, String msg) {
		LOGGER.info("开始发送群消息");
		JSONObject r = new JSONObject();
		r.put("group_uin", groupId);
		r.put("content", JSON.toJSONString(Arrays.asList(msg, Arrays.asList("font", Font.DEFAULT_FONT))));  //注意这里虽然格式是Json，但是实际是String
		r.put("face", 573);
		r.put("clientid", Client_ID);
		r.put("msg_id", MESSAGE_ID++);
		r.put("psessionid", psessionid);

		HttpPost post = defaultHttpPost(ApiURL.SEND_MESSAGE_TO_GROUP, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			Integer result = responseJson.getInteger("errCode");
			if (result != null && result == 0) {
				LOGGER.info("发送群消息成功");
			} else {
				LOGGER.error("发送群消息失败 返回码：" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("发送群消息失败");
		}
	}

	/**
	 * 发送讨论组消息
	 *
	 * @param discussId 讨论组id
	 * @param msg       消息内容
	 */
	public void sendMessageToDiscuss(long discussId, String msg) {
		LOGGER.info("开始发送群消息");
		JSONObject r = new JSONObject();
		r.put("did", discussId);
		r.put("content", JSON.toJSONString(Arrays.asList(msg, Arrays.asList("font", Font.DEFAULT_FONT))));  //注意这里虽然格式是Json，但是实际是String
		r.put("face", 573);
		r.put("clientid", Client_ID);
		r.put("msg_id", MESSAGE_ID++);
		r.put("psessionid", psessionid);

		HttpPost post = defaultHttpPost(ApiURL.SEND_MESSAGE_TO_DISCUSS, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			Integer result = responseJson.getInteger("errCode");
			if (result != null && result == 0) {
				LOGGER.info("发送讨论组消息成功");
			} else {
				LOGGER.error("发送讨论组消息失败 返回码：" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("发送群消息失败");
		}
	}

	/**
	 * 发送消息
	 *
	 * @param friendId 好友id
	 * @param msg      消息内容
	 */
	public void sendMessageToFriend(long friendId, String msg) {
		LOGGER.info("开始发送消息");
		JSONObject r = new JSONObject();
		r.put("to", friendId);
		r.put("content", JSON.toJSONString(Arrays.asList(msg, Arrays.asList("font", Font.DEFAULT_FONT))));  //注意这里虽然格式是Json，但是实际是String
		r.put("face", 573);
		r.put("clientid", Client_ID);
		r.put("msg_id", MESSAGE_ID++);
		r.put("psessionid", psessionid);

		//发送请求的客户端
		HttpPost post = defaultHttpPost(ApiURL.SEND_MESSAGE_TO_FRIEND, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			Integer result = responseJson.getInteger("errCode");
			if (result != null && result == 0) {
				LOGGER.info("发送消息成功");
			} else {
				LOGGER.error("发送消息失败 返回码：" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("发送消息失败");
		}
	}

	/**
	 * 获得讨论组列表
	 *
	 * @return
	 */
	public List<Discuss> getDiscussList() {
		LOGGER.info("开始获取讨论组列表");
		HttpGet get = defaultHttpGet(ApiURL.GET_DISCUSS_LIST, psessionid, vfwebqq);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				return JSON.parseArray(responseJson.getJSONObject("result").getJSONArray("dnamelist").toJSONString(), Discuss.class);
			} else {
				LOGGER.error("获取讨论组列表失败");
			}
		} catch (IOException e) {
			LOGGER.error("获取讨论组列表失败");
		}
		return null;
	}

	/**
	 * 获得好友列表（包含分组信息）
	 *
	 * @return
	 */
	public List<Category> getFriendListWithCategory() {
		LOGGER.info("开始获取好友列表");
		JSONObject r = new JSONObject();
		r.put("vfwebqq", vfwebqq);
		r.put("hash", hash());

		HttpPost post = defaultHttpPost(ApiURL.GET_FRIEND_LIST, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				JSONObject result = responseJson.getJSONObject("result");
				//获得好友信息
				Map<Long, Friend> friendMap = parseFriendMap(result);
				//获得分组
				JSONArray categories = result.getJSONArray("categories");
				Map<Integer, Category> categoryMap = new HashMap<>();
				categoryMap.put(0, Category.defaultCategory());
				for (int i = 0; categories != null && i < categories.size(); i++) {
					Category category = categories.getObject(i, Category.class);
					categoryMap.put(category.getIndex(), category);
				}
				JSONArray friends = result.getJSONArray("friends");
				for (int i = 0; friends != null && i < friends.size(); i++) {
					JSONObject item = friends.getJSONObject(i);
					Friend friend = friendMap.get(item.getLongValue("uin"));
					categoryMap.get(item.getIntValue("categories")).addFriend(friend);
				}
				return new ArrayList<>(categoryMap.values());
			} else {
				LOGGER.error("获取好友列表失败");
			}
		} catch (IOException e) {
			LOGGER.error("获取好友列表失败");
		}
		return null;
	}

	/**
	 * 获取好友列表
	 *
	 * @return
	 */
	public List<Friend> getFriendList() {
		LOGGER.info("开始获取好友列表");
		JSONObject r = new JSONObject();
		r.put("vfwebqq", vfwebqq);
		r.put("hash", hash());

		HttpPost post = defaultHttpPost(ApiURL.GET_FRIEND_LIST, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				JSONObject result = responseJson.getJSONObject("result");
				return new ArrayList<>(parseFriendMap(result).values());
			} else {
				LOGGER.error("获取好友列表失败");
			}
		} catch (IOException e) {
			LOGGER.error("获取好友列表失败");
		}
		return null;
	}

	//将json解析为好友列表
	private static Map<Long, Friend> parseFriendMap(JSONObject result) {
		Map<Long, Friend> friendMap = new HashMap<>();
		JSONArray info = result.getJSONArray("info");
		for (int i = 0; info != null && i < info.size(); i++) {
			JSONObject item = info.getJSONObject(i);
			Friend friend = new Friend();
			friend.setUserId(item.getLongValue("uin"));
			friend.setNickname(item.getString("nick"));
			friendMap.put(friend.getUserId(), friend);
		}
		JSONArray marknames = result.getJSONArray("marknames");
		for (int i = 0; marknames != null && i < marknames.size(); i++) {
			JSONObject item = marknames.getJSONObject(i);
			friendMap.get(item.getLongValue("uin")).setMarkname(item.getString("markname"));
		}
		JSONArray vipinfo = result.getJSONArray("vipinfo");
		for (int i = 0; vipinfo != null && i < vipinfo.size(); i++) {
			JSONObject item = vipinfo.getJSONObject(i);
			Friend friend = friendMap.get(item.getLongValue("u"));
			friend.setVip(item.getIntValue("is_vip") == 1);
			friend.setVipLevel(item.getIntValue("vip_level"));
		}
		return friendMap;
	}

	/**
	 * 获得当前登录用户的详细信息
	 *
	 * @return
	 */
	public UserInfo getAccountInfo() {
		LOGGER.info("开始获取登录用户信息");
		HttpGet get = defaultHttpGet(ApiURL.GET_ACCOUNT_INFO);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				return responseJson.getObject("result", UserInfo.class);
			} else {
				LOGGER.error("获取登录用户信息失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取登录用户信息失败");
		}
		return null;
	}

	/**
	 * 获得好友的详细信息
	 *
	 * @return
	 */
	public UserInfo getFriendInfo(long friendId) {
		LOGGER.info("开始获取好友信息");
		HttpGet get = defaultHttpGet(ApiURL.GET_ACCOUNT_INFO, friendId, vfwebqq, psessionid);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				return responseJson.getObject("result", UserInfo.class);
			} else {
				LOGGER.error("获取好友信息失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取好友信息失败");
		}
		return null;
	}

	/**
	 * 获得最近会话列表
	 *
	 * @return
	 */
	public List<Recent> getRecentList() {
		LOGGER.info("开始获取最近会话列表");
		JSONObject r = new JSONObject();
		r.put("vfwebqq", vfwebqq);
		r.put("clientid", Client_ID);
		r.put("psessionid", "");

		HttpPost post = defaultHttpPost(ApiURL.GET_RECENT_LIST, new BasicNameValuePair("r", r.toJSONString()));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				return JSON.parseArray(responseJson.getJSONArray("result").toJSONString(), Recent.class);
			} else {
				LOGGER.error("获取最近会话列表失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取最近会话列表失败");
		}
		return null;
	}

	/**
	 * 获得qq号
	 *
	 * @param id 用户id
	 * @return
	 */
	public long getQQById(long id) {
		LOGGER.info("开始获取QQ号");

		HttpGet get = defaultHttpGet(ApiURL.GET_QQ_BY_ID, id, vfwebqq);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				return responseJson.getJSONObject("result").getLongValue("account");
			} else {
				LOGGER.error("获取QQ号失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取QQ号失败");
		}
		return 0L;
	}

	/**
	 * 获得登录状态
	 *
	 * @return
	 */
	public List<FriendStatus> getFriendStatus() {
		LOGGER.info("开始获取好友状态");
		HttpGet get = defaultHttpGet(ApiURL.GET_FRIEND_STATUS, vfwebqq, psessionid);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				return JSON.parseArray(responseJson.getJSONArray("result").toJSONString(), FriendStatus.class);
			} else {
				LOGGER.error("获取好友状态失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取好友状态失败");
		}
		return null;
	}

	/**
	 * 获得群的详细信息
	 *
	 * @param groupCode 群编号
	 * @return
	 */
	public GroupInfo getGroupInfo(long groupCode) {
		LOGGER.info("开始获取群资料");
		HttpGet get = defaultHttpGet(ApiURL.GET_GROUP_INFO, groupCode, vfwebqq);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				JSONObject result = responseJson.getJSONObject("result");
				GroupInfo groupInfo = result.getObject("ginfo", GroupInfo.class);
				//获得群成员信息
				Map<Long, GroupUser> groupUserMap = new HashMap<>();
				JSONArray minfo = result.getJSONArray("minfo");
				for (int i = 0; minfo != null && i < minfo.size(); i++) {
					GroupUser groupUser = minfo.getObject(i, GroupUser.class);
					groupUserMap.put(groupUser.getUin(), groupUser);
					groupInfo.addUser(groupUser);
				}
				JSONArray stats = result.getJSONArray("stats");
				for (int i = 0; stats != null && i < stats.size(); i++) {
					JSONObject item = stats.getJSONObject(i);
					GroupUser groupUser = groupUserMap.get(item.getLongValue("uin"));
					groupUser.setClientType(item.getIntValue("client_type"));
					groupUser.setStatus(item.getIntValue("stat"));
				}
				JSONArray cards = result.getJSONArray("cards");
				for (int i = 0; cards != null && i < cards.size(); i++) {
					JSONObject item = cards.getJSONObject(i);
					groupUserMap.get(item.getLongValue("muin")).setCard(item.getString("card"));
				}
				JSONArray vipinfo = result.getJSONArray("vipinfo");
				for (int i = 0; vipinfo != null && i < vipinfo.size(); i++) {
					JSONObject item = vipinfo.getJSONObject(i);
					GroupUser groupUser = groupUserMap.get(item.getLongValue("u"));
					groupUser.setVip(item.getIntValue("is_vip") == 1);
					groupUser.setVipLevel(item.getIntValue("vip_level"));
				}
				return groupInfo;
			} else {
				LOGGER.error("获取群资料失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取群资料失败");
		}
		return null;
	}

	/**
	 * 获得讨论组的详细信息
	 *
	 * @param discussId 讨论组id
	 * @return
	 */
	public DiscussInfo getDiscussInfo(long discussId) {
		LOGGER.info("开始获取讨论组资料");
		HttpGet get = defaultHttpGet(ApiURL.GET_DISCUSS_INFO, discussId, vfwebqq, psessionid);
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get, context)) {
			JSONObject responseJson = JSON.parseObject(getResponseText(response));
			if (0 == responseJson.getIntValue("retcode")) {
				JSONObject result = responseJson.getJSONObject("result");
				DiscussInfo discussInfo = result.getObject("info", DiscussInfo.class);
				//获得讨论组成员信息
				Map<Long, DiscussUser> discussUserMap = new HashMap<>();
				JSONArray minfo = result.getJSONArray("mem_info");
				for (int i = 0; minfo != null && i < minfo.size(); i++) {
					DiscussUser discussUser = minfo.getObject(i, DiscussUser.class);
					discussUserMap.put(discussUser.getUin(), discussUser);
					discussInfo.addUser(discussUser);
				}
				JSONArray stats = result.getJSONArray("mem_status");
				for (int i = 0; stats != null && i < stats.size(); i++) {
					JSONObject item = stats.getJSONObject(i);
					DiscussUser discussUser = discussUserMap.get(item.getLongValue("uin"));
					discussUser.setClientType(item.getIntValue("client_type"));
					discussUser.setStatus(item.getString("status"));
				}
				return discussInfo;
			} else {
				LOGGER.error("获取讨论组资料失败 retcode:" + responseJson.getIntValue("retcode"));
			}
		} catch (IOException e) {
			LOGGER.error("获取讨论组资料失败");
		}
		return null;
	}

	//hash加密方法
	private String hash() {
		return hash(uin, ptwebqq);
	}

	//线程暂停
	private static void sleep(long seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException ignored) {
		}
	}

	//hash加密方法
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

	//得到返回的数据
	private static String getResponseText(CloseableHttpResponse response) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
			StringBuilder buffer = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
			}
			return buffer.toString();
		} catch (IOException e) {
			LOGGER.error("获取返回数据失败");
		}
		return "";
	}

	//默认的http get
	private static HttpGet defaultHttpGet(ApiURL apiUrl, Object... params) {
		String url = apiUrl.buildUrl(params);
		HttpGet get = new HttpGet(url);
		if (apiUrl.getReferer() != null) {
			get.setHeader("Referer", apiUrl.getReferer());
		}
		get.setHeader("User-Agent", ApiURL.getUserAgent());
		return get;
	}

	//默认的http post
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

	//拉取消息的线程
	private class PollMessageTask implements Runnable {

		//请求的post方法
		private HttpPost post;

		//拉取到消息的回调
		private MessageCallback callback;

		public PollMessageTask(HttpPost post, MessageCallback callback) {
			this.post = post;
			RequestConfig config = RequestConfig
					.custom()
					.setConnectTimeout(100000)
					.setSocketTimeout(100000)
					.setConnectionRequestTimeout(100000)
					.build();
			this.post.setConfig(config);
			this.callback = callback;
		}

		@Override
		public void run() {
			try (CloseableHttpClient client = HttpClients.createDefault();
				 CloseableHttpResponse response = client.execute(post, context)) {
				String text = getResponseText(response);

				System.out.println(text);

				JSONObject responseJson = JSON.parseObject(text);

				if (responseJson.getIntValue("retcode") == 0) {
					JSONArray array = responseJson.getJSONArray("result");
					for (int i = 0; array != null && i < array.size(); i++) {
						JSONObject message = array.getJSONObject(i);
						String type = message.getString("poll_type");
						if ("message".equals(type)) {
							callback.onMessage(new Message(message.getJSONObject("value")));
						} else if ("group_message".equals(type)) {
							callback.onGroupMessage(new GroupMessage(message.getJSONObject("value")));
						} else if ("discu_message".equals(type)) {
							callback.onDiscussMessage(new DiscussMessage(message.getJSONObject("value")));
						}
					}
				} else {
					LOGGER.error("接受消息失败 retcode: " + responseJson.getIntValue("retcode"));
				}
			} catch (IOException e) {
				LOGGER.info("暂时没有消息");
			}
		}
	}
}
