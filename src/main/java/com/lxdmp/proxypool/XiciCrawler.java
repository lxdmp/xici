package com.lxdmp.proxypool;

import java.util.*;
import java.util.concurrent.*;
import java.util.Random;
import java.net.URI;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

public class XiciCrawler
{
	private static final String prefix = "http://www.xicidaili.com/nn";
	private int page_idx;
	private int last_validate_interval; // 单位h
	private HttpTerminal terminal = null;
	private ConcurrentMap<Proxy, Date> http_buf = null, https_buf = null;
	
	private int http_added = 0, http_updated = 0, http_deleted = 0;
	private int https_added = 0, https_updated = 0, https_deleted = 0;

	public XiciCrawler(int last_validate_interval) throws Exception
	{
		terminal = new HttpTerminal();
		terminal.setUsrAgent("Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:47.0) Gecko/20100101 Firefox/47.0");
		http_buf=  new ConcurrentHashMap<Proxy, Date>();
		https_buf=  new ConcurrentHashMap<Proxy, Date>();
		this.last_validate_interval = last_validate_interval;
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
		String scheme = e.text().toLowerCase();

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
		new_proxy.setScheme(scheme);
		new_proxy.setRegion(region);
		new_proxy.setLastValidateTime(stamp);
		new_proxy.setSpeed(speed_result);
		return new_proxy;
	}

	private Collection<Proxy> parsePage(String res) throws Exception
	{
		LinkedList<Proxy> ret = new LinkedList<Proxy>();
		
		Document doc = Jsoup.parse(res);
		Element ip_tbl = doc.getElementById("ip_list");
		Elements proxy_nodes = ip_tbl.getElementsByTag("tr");
		for(Element proxy_node : proxy_nodes)
		{
			if(proxy_node!=proxy_nodes.first())
			{
				Proxy new_proxy = this.parseProxyNode(proxy_node);
				ret.addLast(new_proxy);
			}
		}

		if(ret.isEmpty())
			return null;
		else
			return ret;
	}

	private Collection<Proxy> getPage(String url) throws Exception
	{
		URI uri = new URI(url);
		Proxy proxy_used = this.getRandomProxy(uri.getScheme());
		if(proxy_used!=null)
			terminal.addTemporaryProxy(proxy_used);

		System.out.println(url);
		String res = terminal.sendHttpGet(url);
		return this.parsePage(res);
	}

	private Collection<Proxy> nextPage() throws Exception
	{
		Collection<Proxy> ret = getPage(this.formatUrl());
		this.page_idx += 1;
		return ret;
	}

	public void update() throws Exception
	{
		Collection<Proxy> proxys = null;

		// 抓取网络数据
		{
			Calendar c = Calendar.getInstance();
			c.set(Calendar.HOUR, c.get(Calendar.HOUR)-this.last_validate_interval);
			Date t = c.getTime();
			proxys = this.getWebPages(t);
		}

		if(proxys==null || proxys.isEmpty())
			return;

		this.resetStatistic();
		
		// 添加/更新获取到的数据
		Iterator<Proxy> iter = proxys.iterator();
		while(iter.hasNext())
		{
			Proxy proxy = iter.next();
			this.tryAddProxy(proxy);
		}

		// 讲本次更新未覆盖到的数据删除
		{
			Calendar c = Calendar.getInstance();
			c.set(Calendar.MINUTE, c.get(Calendar.MINUTE)-1);
			Date t = c.getTime();
			this.tryRemoveProxy(t);
		}

		System.out.println(this.logStatistic());
	}

	// 获取在指定时间点后验证过的代理信息
	private Collection<Proxy> getWebPages(Date validate_after_this_stamp) throws Exception
	{
		Collection<Proxy> ret = new LinkedList<Proxy>();
		boolean timeout = false;
		this.page_idx = 1;

		do{
			Collection<Proxy> page_result = this.nextPage();
			if(page_result==null || page_result.isEmpty())
				break;
			Iterator<Proxy> iter = page_result.iterator();
			while(iter.hasNext())
			{
				Proxy proxy = iter.next();
				if(proxy.getLastValidateTime().before(validate_after_this_stamp))
				{
					timeout = true;
					break;
				}
				ret.add(proxy);
				System.out.println(proxy);
			}
		}while(!timeout);

		if(ret.isEmpty())
			return null;
		else
			return ret;
	}

	/*
	 * 获取Proxy对象.
	 */
	private int getRandomNum(int range_start, int range_end)
	{
		return new Random().nextInt(range_end-range_start) + range_start;
	}

	private Proxy getRandomProxy(ConcurrentMap<Proxy, Date> buf)
	{
		if(buf.isEmpty())
			return null;
		int idx = getRandomNum(0, http_buf.size()-1);
		Iterator<Proxy> iter = buf.keySet().iterator();
		while(iter.hasNext() && idx-->0)
			iter.next();
		return iter.next();
	}

	private Proxy getRandomProxy(String scheme)
	{
		if(scheme.compareTo("http")==0)
			return getRandomProxy(http_buf);
		else if(scheme.compareTo("https")==0)
			return getRandomProxy(https_buf);
		return null;
	}

	/*
	 * 新增/更新/删除统计.
	 */
	private void resetStatistic()
	{
		http_added = 0; http_updated = 0; http_deleted = 0;
		https_added = 0; https_updated = 0; https_deleted = 0;
	}

	private String logStatistic()
	{
		return String.format(
			"http : %d added, %d updated, %d deleted, %d in total now;\nhttps : %d added, %d updated, %d deleted, %d in total now", 
			http_added, http_updated, http_deleted, this.http_buf.size(), 
			https_added, https_updated, https_deleted, this.https_buf.size()
		);
	}

	private void tryAddProxy(Proxy proxy)
	{
		Date actual = Calendar.getInstance().getTime();
		
		if(proxy.getScheme().compareTo("http")==0)
		{
			if(this.http_buf.get(proxy)==null){
				this.http_buf.put(proxy, actual);
				http_added++;
			}else{
				this.http_buf.replace(proxy, actual);
				http_updated++;
			}
		}
		else if(proxy.getScheme().compareTo("https")==0)
		{
			if(this.https_buf.get(proxy)==null){
				this.https_buf.put(proxy, actual);
				https_added++;
			}else{
				this.https_buf.replace(proxy, actual);
				https_updated++;
			}
		}
	}

	// 更新时戳早于stamp的将被删除
	private void tryRemoveProxy(Date stamp)
	{
		LinkedList<Proxy> to_be_deleted = new LinkedList<Proxy>();
		
		for(Map.Entry<Proxy, Date> entry : this.http_buf.entrySet())
		{
			if(entry.getValue().before(stamp))
				to_be_deleted.add(entry.getKey());
		}
		
		for(Map.Entry<Proxy, Date> entry : this.https_buf.entrySet())
		{
			if(entry.getValue().before(stamp))
				to_be_deleted.add(entry.getKey());
		}

		Iterator<Proxy> iter = to_be_deleted.iterator();
		while(iter.hasNext())
		{
			Proxy proxy = iter.next();
			if(proxy.getScheme().compareTo("http")==0)
			{
				http_deleted++;
				this.http_buf.remove(proxy);
			}
			else if(proxy.getScheme().compareTo("https")==0)
			{
				https_deleted++;
				this.https_buf.remove(proxy);
			}
		}
	}
}

