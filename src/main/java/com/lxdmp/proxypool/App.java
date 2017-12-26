package com.lxdmp.proxypool;

import java.util.*;
import java.text.SimpleDateFormat;

public class App 
{
    public static void main(String[] args)
    {
		try{
			while(true)
			{
				XiciCrawler crawler = new XiciCrawler(3);
				crawler.update();
				Thread.sleep(60*1000);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
    }
}

