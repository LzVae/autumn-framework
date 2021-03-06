package com.zxd.autumn.framework.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.zxd.autumn.framework.helper.BeanHelper;
import com.zxd.autumn.framework.helper.ConfigHelper;
import com.zxd.autumn.framework.helper.ControllerHelper;
import com.zxd.autumn.framework.main.InitLoader;
import com.zxd.autumn.framework.util.CodecUtil;
import com.zxd.autumn.framework.util.JSONUtil;
import com.zxd.autumn.framework.util.ReflectionUtil;
import com.zxd.autumn.framework.util.StreamUtil;
import com.zxd.autumn.framework.web.Data;
import com.zxd.autumn.framework.web.Handler;
import com.zxd.autumn.framework.web.Param;
import com.zxd.autumn.framework.web.View;

/**
* @Title: DispatcherServlet.java  
* @Package com.zxd.autumn.framework.core  
* @Description: 框架的核心，请求转发器
* @author zhuxindong  E-mail:501801307@qq.com
* @date 创建时间：2018年4月14日 下午2:26:00
* @version 1.0
*/
@WebServlet(urlPatterns = "/*", loadOnStartup = 0)
public class DispatcherServlet extends HttpServlet{
	
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		
		/** 初始化Helper类 */
		InitLoader.initHelper();
		
		/** 获取ServletContext对象，用于注册Servlet */
		ServletContext servletContext = servletConfig.getServletContext();
		
		/** 注册用于处理JSP的Servlet */
		ServletRegistration jspServlet = servletContext.getServletRegistration("jsp");
		jspServlet.addMapping(ConfigHelper.getAppJspPath()+"*");
		
		/** 注册用于处理静态资源的默认Servlet */
		ServletRegistration defaultServlet = servletContext.getServletRegistration("default");
		defaultServlet.addMapping(ConfigHelper.getAppStaticPath()+"*");
		
	}
	
	
	
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		/** 获取请求方法与请求路径 */
		String requestMethod = req.getMethod().toLowerCase();
		String requestPath = req.getPathInfo();
		
		/** 根据请求方法与如今获取对应的请求处理器 */
		Handler handler = ControllerHelper.getHandler(requestMethod, requestPath);
		
		if (handler != null) {
			
			/** 获取对应的Controller类及其Bean实例 */
			Class<?> controllerClass = handler.getControllerClass();
			Object controllerBean = BeanHelper.getBean(controllerClass);
			
			/** 创建存放请求参数对象的Map,
			 *  即Map<paramName, ParamValue>
			 */
			Map<String, Object> paramMap = new HashMap<String, Object>();
			
			/** 从request中获取请求参数的枚举 */
			Enumeration<String> paramNames = req.getParameterNames();
			
			/** 遍历 */
			while (paramNames.hasMoreElements()) {
				
				String paramName = (String) paramNames.nextElement();
				
				/** 获取参数值并放入paramMap */
				String paramValue = req.getParameter(paramName);
				paramMap.put(paramName, paramValue);
				
			}
			
			
			/** -----上面是post过来的参数，下面是通过url以get方式过来的参数----- */
			String body = CodecUtil.decodeURL(StreamUtil.getString(
												req.getInputStream()));
			
			if (StringUtils.isNotEmpty(body)) {
				
				String[] params = StringUtils.split(body, "&");
				
				if (ArrayUtils.isNotEmpty(params)) {
					
					for (String param : params) {
						
						String[] array = StringUtils.split(param, "=");
						
						if (ArrayUtils.isNotEmpty(array) && array.length == 2) {
							
							String paramName = array[0];
							String paramValue = array[1];
							
							paramMap.put(paramName, paramValue);
						}
						
					}
					
				}
				
			}
			
			
			
			
			/** ---------获取完参数后，封装成Param对象------------ */
			Param param = new Param(paramMap);
			
			/** 调用带有 @RequestMapping 注解的方法 */
			Method requestMappingMethod = handler.getRequestMappingMethod();
			Object result = ReflectionUtil.invokeMethod(controllerBean,
												requestMappingMethod, param);
			
			/** 处理返回值 */
			if (result instanceof View) {
				
				/** 返回对应的JSP页面 */
				View view = (View) result;
				
				String path = view.getPath();
				
				if (StringUtils.isNotEmpty(path)) {
					
					if (path.startsWith("/")) {
						
						resp.sendRedirect(req.getContextPath()+path);
						
					}else {
						
						Map<String, Object> model = view.getModel();
						
						for (Map.Entry<String, Object> entry : model.entrySet()) {
							
							req.setAttribute(entry.getKey(), entry.getValue());
							
						}
						
						req.getRequestDispatcher(ConfigHelper.getAppJspPath()+path).forward(req, resp);
						
					}
					
				}
				
				
			}else if (result instanceof Data) {
				
				/** 返回JSON数据 */
				Data data = (Data) result;
				
				Object model = data.getModel();
				
				if (model != null) {
					
					resp.setContentType("application/json");
					resp.setCharacterEncoding("UTF-8");
					
					PrintWriter writer = resp.getWriter();
					String json = JSONUtil.toJSON(model);
					
					writer.write(json);
					writer.flush();
					writer.close();
					
				}
				
				
				
			}
			
			
		}
			
	}
	
	
	

}
