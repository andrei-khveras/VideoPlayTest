package com.khveras.videotest;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.Point;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.testng.Assert;
import org.testng.annotations.Test;

public class YoutubePlayTest {

	public static final String TEST_PAGE = "http://www.wolterskluwer.com/About-Us/Our-Customers/Pages/Home.aspx";

	public static final String PROXY_LOCATION = "localhost:5005";

	@Test
	public void isVideoPlayingStarted() throws AWTException {
		// Preparing simple FiltersSource for requests tracking
		final Map<String, HttpResponse> trackedResponces = new HashMap<String, HttpResponse>();
		HttpFiltersSource filterSource = new HttpFiltersSourceAdapter() {
			@Override
			public HttpFilters filterRequest(HttpRequest originalRequest) {
				// System.out.println("Request triggered to " + originalRequest.getUri());
				return new HttpFiltersAdapter(originalRequest) {
					@Override
					public HttpObject responsePre(HttpObject httpObject) {
						if (httpObject instanceof HttpResponse) {
							trackedResponces.put(originalRequest.getUri(), (HttpResponse) httpObject);
						}
						return httpObject;
					}
				};
			}
		};

		// Starting proxy
		DefaultHttpProxyServer.bootstrap().withPort(5005).withFiltersSource(filterSource).start();

		// Preparing proxy configuration for WebDriver
		Proxy proxy = new org.openqa.selenium.Proxy();
		proxy.setHttpProxy(PROXY_LOCATION).setFtpProxy(PROXY_LOCATION).setSslProxy(PROXY_LOCATION);

		// Creating FireFox driver
		DesiredCapabilities cap = new DesiredCapabilities();
		cap.setCapability(CapabilityType.PROXY, proxy);
		WebDriver webDriver = new FirefoxDriver(cap);

		webDriver.get(TEST_PAGE);
		
		// NOT THE BEST WAY TO CLICK SOMETHING IN TEST
		webDriver.findElement(By.tagName("body")).sendKeys(Keys.F11);
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		double scale = (double) Toolkit.getDefaultToolkit().getScreenSize().width / (double) webDriver.manage().window().getSize().width;

		WebElement iframe = webDriver.findElement(By.tagName("iframe"));
		Point framePosition = iframe.getLocation();
		Robot robot = new Robot();
		robot.mouseMove((int) (framePosition.x * scale) + iframe.getSize().getWidth() / 2, (int) (framePosition.y * scale)
								+ iframe.getSize().getHeight() / 2);
		robot.mousePress(InputEvent.BUTTON1_MASK);
		robot.delay(300);
		robot.mouseRelease(InputEvent.BUTTON1_MASK);

		// Collecting requests
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Looking for videostram request
		boolean videoStarted = false;
		for (Map.Entry<String, HttpResponse> request : trackedResponces.entrySet()) {
			String contentType = request.getValue().headers().get("Content-Type").toString();
			if (null == contentType) {
				continue;
			}

			if ("application/octet-stream".equals(contentType)) {
				videoStarted = true;
				break;
			}
		}
		
		webDriver.quit();

		Assert.assertTrue(videoStarted, "Video was not started");
	}
}
