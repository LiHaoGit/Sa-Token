/*
 * Copyright 2020-2099 sa-token.cc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.dev33.satoken.oauth2.consts;

/**
 * Sa-Token OAuth2 所有常量
 *
 * @author click33
 * @since 1.23.0
 */
public class SaOAuth2Consts {

	/**
	 * 所有API接口 
	 * @author click33 
	 */
	public static final class Api {
		public static String authorize = "/oauth2/authorize";
		public static String token = "/oauth2/token";
		public static String refresh = "/oauth2/refresh";
		public static String revoke = "/oauth2/revoke";
		public static String client_token = "/oauth2/client_token";
		public static String doLogin = "/oauth2/doLogin";
		public static String doConfirm = "/oauth2/doConfirm";
	}
	
	/**
	 * 所有参数名称 
	 * @author click33 
	 */
	public static final class Param {
		public static String response_type = "response_type";
		public static String client_id = "client_id";
		public static String client_secret = "client_secret";
		public static String redirect_uri = "redirect_uri";
		public static String scope = "scope";
		public static String state = "state";
		public static String code = "code";
		public static String token = "token";
		public static String access_token = "access_token";
		public static String refresh_token = "refresh_token";
		public static String grant_type = "grant_type";
		public static String username = "username";
		public static String password = "password";
		public static String name = "name";
		public static String pwd = "pwd";
	}

	/**
	 * 所有返回类型 
	 */
	public static final class ResponseType {
		public static String code = "code";
		public static String token = "token";
	}

	/**
	 * 所有授权类型 
	 */
	public static final class GrantType {
		public static String authorization_code = "authorization_code";
		public static String refresh_token = "refresh_token";
		public static String password = "password";
		public static String client_credentials = "client_credentials";
		public static String implicit = "implicit";
	}
	
	/** 表示OK的返回结果 */
	public static final String OK = "ok";

	/** 表示请求没有得到任何有效处理 {msg: "not handle"} */
	public static final String NOT_HANDLE = "{\"msg\": \"not handle\"}";
	
}
