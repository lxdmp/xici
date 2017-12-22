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
	
	private void parseProxyNode(Element node)
	{
		System.out.println("find node : \n"+node);
	}

	private void parsePage(String res)
	{
		Document doc = Jsoup.parse(res);
		Element ip_tbl = doc.getElementById("ip_list");
		Elements proxy_nodes = ip_tbl.getElementsByTag("tr");
		for(Element proxy_node : proxy_nodes)
			this.parseProxyNode(proxy_node);
	}

	private void getPage(String url)
	{
		String res = terminal.sendHttpGet(url);
		this.parsePage(res);
	}

	private void nextPage()
	{
		getPage(this.formatUrl());
		this.page_idx += 1;
	}

	public void update()
	{
		this.nextPage();
	}
}
