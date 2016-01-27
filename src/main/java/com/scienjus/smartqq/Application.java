package com.scienjus.smartqq;

import com.scienjus.smartqq.callback.MessageCallback;
import com.scienjus.smartqq.client.SmartQQClient;
import com.scienjus.smartqq.model.DiscussMessage;
import com.scienjus.smartqq.model.GroupMessage;
import com.scienjus.smartqq.model.Message;

/**
 * @author XieEnlong
 * @date 2015/12/18.
 */
public class Application {

	public static void main(String[] args) {
		SmartQQClient client = new SmartQQClient();
//		SmartQQAction client = new SmartQQAction();
		client.login();
		client.getGroupList();
		client.pollMessage(new MessageCallback() {
			@Override
			public void onMessage(Message message) {
				System.out.println(message);
			}

			@Override
			public void onGroupMessage(GroupMessage message) {
				System.out.println(message);
			}

			@Override
			public void onDiscussMessage(DiscussMessage message) {
				System.out.println(message);
			}
		});
	}
}
