package com.lxdmp.proxypool;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

public class XiciCrawler
{
	private static String prefix = "http://www.xicidaili.com/nn";
	private int page_idx = 1;
	private HttpTerminal terminal = null;

	public XiciCrawler()
	{
		terminal = new HttpTerminal();
		terminal.setUsrAgent("Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:47.0) Gecko/20100101 Firefox/47.0");
	}

	private String formatUrl()
	{
		return String.format("%s/%d", prefix, page_idx);
	}
	
	private Proxy parseProxyNode(Element node) throws Exception
	{
		Element e = null;
		Attributes attrs = null;

		// 地址
		e = node.child(1);
		String ip = e.text();

		// 端口
		e = node.child(2);
		int port = Integer.parseInt(e.text());

		// 区域
		e = node.child(3);
		while(e.childNodeSize()>1)
			e = e.child(0);
		String region = e.text();

		// 类型
		e = node.child(5);
		String type = e.text().toLowerCase();

		// 速度(综合速度与连接时间)
		int speed_result = 0;

		e = node.child(6);
		while(e.childNodeSize()>1)
			e = e.child(0);
		String pure_speed = e.attributes().get("style");

		e = node.child(7);
		while(e.childNodeSize()>1)
			e = e.child(0);
		String conn_speed = e.attributes().get("style");

		String[] speeds = {pure_speed, conn_speed};
		for(int i=0; i<speeds.length; ++i)
		{
			String s = speeds[i];
			String[] s_l = s.split(":");
			if(s_l.length!=2)
				throw new Exception("Format changed : "+s+"???");
			speed_result += Integer.parseInt(s_l[1].substring(0, s_l[1].length()-1));
		}
		speed_result /= speeds.length;

		// 最后验证时间
		e = node.child(9);
		String stamp = e.text();

		Proxy new_proxy = new Proxy();
		new_proxy.setIp(ip);
		new_proxy.setPort(port);
		new_proxy.setType(type);
		new_proxy.setRegion(region);
		new_proxy.setLastValidateTime(stamp);
		new_proxy.setSpeed(speed_result);
		return new_proxy;
	}

	private void parsePage(String res) throws Exception
	{
		Document doc = Jsoup.parse(res);
		Element ip_tbl = doc.getElementById("ip_list");
		Elements proxy_nodes = ip_tbl.getElementsByTag("tr");
		for(Element proxy_node : proxy_nodes)
		{
			if(proxy_node!=proxy_nodes.first())
			{
				Proxy new_proxy = this.parseProxyNode(proxy_node);
				System.out.println(new_proxy);
			}
		}
	}

	private void getPage(String url) throws Exception
	{
		String res = terminal.sendHttpGet(url);
		this.parsePage(res);
	}

	private void nextPage() throws Exception
	{
		getPage(this.formatUrl());
		this.page_idx += 1;
	}

	public void update(/*Date update_after_this_stamp*/) throws Exception
	{
		this.nextPage();
	}
}

