package my.websecurity.rememberme.impl;

import java.net.URLDecoder;
import java.net.URLEncoder;

import javax.servlet.http.Cookie;

import my.websecurity.exception.SecurityRuntimeException;
import my.websecurity.rememberme.RememberMeInfo;
import my.websecurity.rememberme.RememberMeService;
import my.websecurity.support.SecurityServletContext;
import my.websecurity.support.metadata.UserDetails;
import my.websecurity.support.metadata.impl.SimpleUserDetails;
import my.websecurity.util.Base64Utils;
import my.websecurity.util.EncryptUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author xiegang
 * @since 2012-7-4
 *
 */
public abstract class CookieRememberMeInfoSupport implements RememberMeInfo {
	private static final Logger logger = LoggerFactory.getLogger(CookieRememberMeInfoSupport.class);
	
	private static final String KEY = "cybertechwebsecurity";
	
	/**
	 * 通过用户名查询用户的密码<br/>
	 * 不存在时返回null
	 * 
	 * @param username
	 * @return
	 */
	protected abstract String queryPassword(String username);

	@Override
	public UserDetails loadRememberMeInfo(SecurityServletContext context, String rememberMeKey, int maxAge) throws SecurityRuntimeException {
		try {
			String rememberMeCookieValue = null;
			Cookie[] cookies = context.getRequest().getCookies();
			if(null != cookies) {
				for(int i = 0; i < cookies.length; i++) {
					if(rememberMeKey.equals(cookies[i].getName())) {
						rememberMeCookieValue = cookies[i].getValue();
						break;
					}
				}
			}
			if(null != rememberMeCookieValue && rememberMeCookieValue.trim().length() > 0) {
				String one = new String(Base64Utils.decode(URLDecoder.decode(rememberMeCookieValue, "UTF-8").getBytes()));
				String[] parts = one.split(":");
				// parts
				// des:username, timeseq, md5:usernaem+timeseq+password
				if(parts.length == 3 && parts[0].length() > 0 && parts[1].length() > 0 && parts[2].length() > 0) {
					long timeSeq = -1L;
					try { timeSeq = Long.parseLong(parts[1]); } catch (NumberFormatException e) {}
					if (timeSeq == -1L || System.currentTimeMillis() - timeSeq > (maxAge * 1000)) {
						// 超时失效
						return null;
					}
					String username = EncryptUtils.DESDecrypt(parts[0], KEY);
					String password = queryPassword(username);
					if(null != password && password.length() > 0) {
						if(EncryptUtils.MD5Encode(username + ":" + parts[1] + ":" + password).equals(parts[2])) {
							// success
							return new SimpleUserDetails(username, password);
						}
					}
				}
			}
			
			return null;
		} catch (Exception e) {
			logger.warn("loadRememberMeDetails", e);
			throw new SecurityRuntimeException(e);
		}
	}
	
	@Override
	public void saveRememberMeInfo(SecurityServletContext context, UserDetails userDetails, String rememberMeKey, int maxAge) throws SecurityRuntimeException {
		try {
			long timeSeq = System.currentTimeMillis();
			
			// parts
			// des:username, timeseq, md5:usernaem+timeseq+password
			String str = EncryptUtils.DESEncrypt(userDetails.getName(), KEY) + ":" + timeSeq + ":" + EncryptUtils.MD5Encode(userDetails.getName() + ":" + timeSeq + ":" + userDetails.getPassword());
			String rememberMeString = URLEncoder.encode(new String(Base64Utils.encode(str.getBytes())), "UTF-8");
			rememberMeString = rememberMeString.replaceAll("\\s", ""); // 76
			Cookie cookie = new Cookie(rememberMeKey, rememberMeString);
			cookie.setMaxAge(maxAge > 0 ? maxAge: RememberMeService.REMEMBERME_MAX_AGE);
			cookie.setPath(context.getRequest().getContextPath() + '/');
			context.getResponse().addCookie(cookie);
		} catch (Exception e) {
			logger.error("saveRememberMeInfo", e);
			throw new SecurityRuntimeException(e);
		}
	}

	@Override
	public void removeRememberMeInfo(SecurityServletContext context, String rememberMeKey) throws SecurityRuntimeException {
		//销毁cookie
		Cookie cookie= new Cookie(rememberMeKey, null);
		cookie.setMaxAge(0);
		cookie.setPath(context.getRequest().getContextPath() + '/');
		context.getResponse().addCookie(cookie);
	}
}
