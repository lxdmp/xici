package com.lxdmp.proxypool;

import java.util.*;
import java.text.SimpleDateFormat;

public class App 
{
    public static void main(String[] args)
    {
		try{
			XiciCrawler crawler = new XiciCrawler();
			//crawler.update();
			Calendar c = new GregorianCalendar();
			c.set(2017, 12-1, 25, 8, 0, 0);
			crawler.getWebPages(c.getTime());
		}catch(Exception e){
			e.printStackTrace();
		}
    }
}

