package com.lxdmp.proxypool;

import java.util.*;

public class App 
{
    public static void main(String[] args)
    {
		try{
			XiciCrawler crawler = new XiciCrawler();
			crawler.update();
		}catch(Exception e){
			e.printStackTrace();
		}
    }
}

