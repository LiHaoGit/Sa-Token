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
package cn.dev33.satoken.oauth2.template;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import cn.dev33.satoken.oauth2.consts.SaOAuth2Consts.Param;
import cn.dev33.satoken.oauth2.error.SaOAuth2ErrorCode;
import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.dev33.satoken.oauth2.model.*;
import cn.dev33.satoken.strategy.SaStrategy;
import cn.dev33.satoken.util.SaFoxUtil;

import java.util.List;

/**
 * Sa-Token-OAuth2 模块 代码实现
 *
 * @author click33
 * @since 1.23.0
 */
public class SaOAuth2Template {

	// ------------------- 数据加载

	/**
	 * 根据id获取Client信息
	 * @param clientId 应用id
	 * @return ClientModel
	 */
	public SaClientModel getClientModel(String clientId) {
		return SaOAuth2Manager.getDataLoader().getClientModel(clientId);
	}

	/**
	 * 根据ClientId 和 LoginId 获取openid
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return 此账号在此Client下的openid
	 */
	public String getOpenid(String clientId, Object loginId) {
		return SaOAuth2Manager.getDataLoader().getOpenid(clientId, loginId);
	}

	// ------------------- 资源校验API
	/**
	 * 根据id获取Client信息, 如果Client为空，则抛出异常
	 * @param clientId 应用id
	 * @return ClientModel
	 */
	public SaClientModel checkClientModel(String clientId) {
		SaClientModel clientModel = getClientModel(clientId);
		if(clientModel == null) {
			throw new SaOAuth2Exception("无效client_id: " + clientId).setCode(SaOAuth2ErrorCode.CODE_30105);
		}
		return clientModel;
	}
	/**
	 * 获取 Access-Token，如果AccessToken为空则抛出异常
	 * @param accessToken .
	 * @return .
	 */
	public AccessTokenModel checkAccessToken(String accessToken) {
		AccessTokenModel at = getAccessToken(accessToken);
		SaOAuth2Exception.throwBy(at == null, "无效access_token：" + accessToken, SaOAuth2ErrorCode.CODE_30106);
		return at;
	}
	/**
	 * 获取 Client-Token，如果ClientToken为空则抛出异常
	 * @param clientToken .
	 * @return .
	 */
	public ClientTokenModel checkClientToken(String clientToken) {
		ClientTokenModel ct = getClientToken(clientToken);
		SaOAuth2Exception.throwBy(ct == null, "无效：client_token" + clientToken, SaOAuth2ErrorCode.CODE_30107);
		return ct;
	}
	/**
	 * 获取 Access-Token 所代表的LoginId
	 * @param accessToken Access-Token
	 * @return LoginId
	 */
	public Object getLoginIdByAccessToken(String accessToken) {
		return checkAccessToken(accessToken).loginId;
	}
	/**
	 * 校验：指定 Access-Token 是否具有指定 Scope
	 * @param accessToken Access-Token
	 * @param scopes 需要校验的权限列表
	 */
	public void checkScope(String accessToken, String... scopes) {
		if(scopes == null || scopes.length == 0) {
			return;
		}
		AccessTokenModel at = checkAccessToken(accessToken);
		List<String> scopeList = SaFoxUtil.convertStringToList(at.scope);
		for (String scope : scopes) {
			SaOAuth2Exception.throwBy( ! scopeList.contains(scope), "该 Access-Token 不具备 Scope：" + scope, SaOAuth2ErrorCode.CODE_30108);
		}
	}
	/**
	 * 校验：指定 Client-Token 是否具有指定 Scope
	 * @param clientToken Client-Token
	 * @param scopes 需要校验的权限列表
	 */
	public void checkClientTokenScope(String clientToken, String... scopes) {
		if(scopes == null || scopes.length == 0) {
			return;
		}
		ClientTokenModel ct = checkClientToken(clientToken);
		List<String> scopeList = SaFoxUtil.convertStringToList(ct.scope);
		for (String scope : scopes) {
			SaOAuth2Exception.throwBy( ! scopeList.contains(scope), "该 Client-Token 不具备 Scope：" + scope, SaOAuth2ErrorCode.CODE_30109);
		}
	}

	// ------------------- generate 构建数据
	/**
	 * 构建Model：请求Model
	 * @param req SaRequest对象
	 * @param loginId 账号id
	 * @return RequestAuthModel对象
	 */
	public RequestAuthModel generateRequestAuth(SaRequest req, Object loginId) {
		RequestAuthModel ra = new RequestAuthModel();
		ra.clientId = req.getParamNotNull(Param.client_id);
		ra.responseType = req.getParamNotNull(Param.response_type);
		ra.redirectUri = req.getParamNotNull(Param.redirect_uri);
		ra.state = req.getParam(Param.state);
		ra.scope = req.getParam(Param.scope, "");
		ra.loginId = loginId;
		return ra;
	}
	/**
	 * 构建Model：Code授权码
	 * @param ra 请求参数Model
	 * @return 授权码Model
	 */
	public CodeModel generateCode(RequestAuthModel ra) {

		// 删除旧Code
		deleteCode(getCodeValue(ra.clientId, ra.loginId));

		// 生成新Code
		String code = randomCode(ra.clientId, ra.loginId, ra.scope);
		CodeModel cm = new CodeModel(code, ra.clientId, ra.scope, ra.loginId, ra.redirectUri);

		// 保存新Code
		saveCode(cm);
		saveCodeIndex(cm);

		// 返回
		return cm;
	}
	/**
	 * 构建Model：Access-Token
	 * @param code 授权码Model
	 * @return AccessToken Model
	 */
	public AccessTokenModel generateAccessToken(String code) {

		// 1、先校验
		CodeModel cm = getCode(code);
		SaOAuth2Exception.throwBy(cm == null, "无效code", SaOAuth2ErrorCode.CODE_30110);

		// 2、删除旧Token
		deleteAccessToken(getAccessTokenValue(cm.clientId, cm.loginId));
		deleteRefreshToken(getRefreshTokenValue(cm.clientId, cm.loginId));

		// 3、生成token
		AccessTokenModel at = convertCodeToAccessToken(cm);
		RefreshTokenModel rt = convertAccessTokenToRefreshToken(at);
		at.refreshToken = rt.refreshToken;
		at.refreshExpiresTime = rt.expiresTime;

		// 4、保存token
		saveAccessToken(at);
		saveAccessTokenIndex(at);
		saveRefreshToken(rt);
		saveRefreshTokenIndex(rt);

		// 5、删除此Code
		deleteCode(code);
		deleteCodeIndex(cm.clientId, cm.loginId);

		// 6、返回 Access-Token
		return at;
	}
	/**
	 * 刷新Model：根据 Refresh-Token 生成一个新的 Access-Token
	 * @param refreshToken Refresh-Token值
	 * @return 新的 Access-Token
	 */
	public AccessTokenModel refreshAccessToken(String refreshToken) {

		// 获取 Refresh-Token 信息
		RefreshTokenModel rt = getRefreshToken(refreshToken);
		SaOAuth2Exception.throwBy(rt == null, "无效refresh_token: " + refreshToken, SaOAuth2ErrorCode.CODE_30111);
		
		// 如果配置了[每次刷新产生新的Refresh-Token]
		if(checkClientModel(rt.clientId).getIsNewRefresh()) {
			// 删除旧 Refresh-Token
			deleteRefreshToken(rt.refreshToken);

			// 创建并保持新的 Refresh-Token
			rt = convertRefreshTokenToRefreshToken(rt);
			saveRefreshToken(rt);
			saveRefreshTokenIndex(rt);
		}

		// 删除旧 Access-Token
		deleteAccessToken(getAccessTokenValue(rt.clientId, rt.loginId));

		// 生成新 Access-Token
		AccessTokenModel at = convertRefreshTokenToAccessToken(rt);

		// 保存新 Access-Token
		saveAccessToken(at);
		saveAccessTokenIndex(at);

		// 返回新 Access-Token
		return at;
	}
	/**
	 * 构建Model：Access-Token (根据RequestAuthModel构建，用于隐藏式 and 密码式)
	 * @param ra 请求参数Model
	 * @param isCreateRt 是否生成对应的Refresh-Token
	 * @return Access-Token Model
	 */
	public AccessTokenModel generateAccessToken(RequestAuthModel ra, boolean isCreateRt) {

		// 1、删除 旧Token
		deleteAccessToken(getAccessTokenValue(ra.clientId, ra.loginId));
		if(isCreateRt) {
			deleteRefreshToken(getRefreshTokenValue(ra.clientId, ra.loginId));
		}

		// 2、生成 新Access-Token
		String newAtValue = randomAccessToken(ra.clientId, ra.loginId, ra.scope);
		AccessTokenModel at = new AccessTokenModel(newAtValue, ra.clientId, ra.loginId, ra.scope);
		at.openid = getOpenid(ra.clientId, ra.loginId);
		at.expiresTime = System.currentTimeMillis() + (checkClientModel(ra.clientId).getAccessTokenTimeout() * 1000);

		// 3、生成&保存 Refresh-Token
		if(isCreateRt) {
			RefreshTokenModel rt = convertAccessTokenToRefreshToken(at);
			saveRefreshToken(rt);
			saveRefreshTokenIndex(rt);
		}

		// 5、保存 新Access-Token
		saveAccessToken(at);
		saveAccessTokenIndex(at);

		// 6、返回 新Access-Token
		return at;
	}
	/**
	 * 构建Model：Client-Token
	 * @param clientId 应用id
	 * @param scope 授权范围
	 * @return Client-Token Model
	 */
	public ClientTokenModel generateClientToken(String clientId, String scope) {
		// 1、删掉旧 Past-Token
		deleteClientToken(getPastTokenValue(clientId));

		// 2、将旧Client-Token 标记为新 Past-Token
		ClientTokenModel oldCt = getClientToken(getClientTokenValue(clientId));
		savePastTokenIndex(oldCt);
		
		// 2.5、如果配置了 PastClientToken 的 ttl ，则需要更新一下 
		SaClientModel cm = checkClientModel(clientId);
		if(oldCt != null && cm.getPastClientTokenTimeout() != -1) {
			oldCt.expiresTime = System.currentTimeMillis() + (cm.getPastClientTokenTimeout() * 1000);
			saveClientToken(oldCt);
		}

		// 3、生成新Client-Token
		ClientTokenModel ct = new ClientTokenModel(randomClientToken(clientId, scope), clientId, scope);
		ct.expiresTime = System.currentTimeMillis() + (cm.getClientTokenTimeout() * 1000);

		// 3、保存新Client-Token 
		saveClientToken(ct);
		saveClientTokenIndex(ct);

		// 4、返回
		return ct;
	}
	/**
	 * 构建URL：下放Code URL (Authorization Code 授权码)
	 * @param redirectUri 下放地址
	 * @param code code参数
	 * @param state state参数
	 * @return 构建完毕的URL
	 */
	public String buildRedirectUri(String redirectUri, String code, String state) {
		String url = SaFoxUtil.joinParam(redirectUri, Param.code, code);
		if( ! SaFoxUtil.isEmpty(state)) {
			url = SaFoxUtil.joinParam(url, Param.state, state);
		}
		return url;
	}
	/**
	 * 构建URL：下放Access-Token URL （implicit 隐藏式）
	 * @param redirectUri 下放地址
	 * @param token token
	 * @param state state参数
	 * @return 构建完毕的URL
	 */
	public String buildImplicitRedirectUri(String redirectUri, String token, String state) {
		String url = SaFoxUtil.joinSharpParam(redirectUri, Param.token, token);
		if( ! SaFoxUtil.isEmpty(state)) {
			url = SaFoxUtil.joinSharpParam(url, Param.state, state);
		}
		return url;
	}
	/**
	 * 回收 Access-Token
	 * @param accessToken Access-Token值
	 */
	public void revokeAccessToken(String accessToken) {

		// 如果查不到任何东西, 直接返回
		AccessTokenModel at = getAccessToken(accessToken);
		if(at == null) {
			return;
		}

		// 删除 Access-Token
		deleteAccessToken(accessToken);
		deleteAccessTokenIndex(at.clientId, at.loginId);

		// 删除对应的 Refresh-Token
		String refreshToken = getRefreshTokenValue(at.clientId, at.loginId);
		deleteRefreshToken(refreshToken);
		deleteRefreshTokenIndex(at.clientId, at.loginId);
	}

	// ------------------- check 数据校验
	/**
	 * 判断：指定 loginId 是否对一个 Client 授权给了指定 Scope
	 * @param loginId 账号id
	 * @param clientId 应用id
	 * @param scope 权限
	 * @return 是否已经授权
	 */
	public boolean isGrant(Object loginId, String clientId, String scope) {
		List<String> grantScopeList = SaFoxUtil.convertStringToList(getGrantScope(clientId, loginId));
		List<String> scopeList = SaFoxUtil.convertStringToList(scope);
		return scopeList.size() == 0 || grantScopeList.containsAll(scopeList);
	}
	/**
	 * 校验：该Client是否签约了指定的Scope
	 * @param clientId 应用id
	 * @param scope 权限(多个用逗号隔开)
	 */
	public void checkContract(String clientId, String scope) {
		List<String> clientScopeList = SaFoxUtil.convertStringToList(checkClientModel(clientId).contractScope);
		List<String> scopelist = SaFoxUtil.convertStringToList(scope);
		if( ! clientScopeList.containsAll(scopelist)) {
			throw new SaOAuth2Exception("请求的Scope暂未签约").setCode(SaOAuth2ErrorCode.CODE_30112);
		}
	}
	/**
	 * 校验：该Client使用指定url作为回调地址，是否合法
	 * @param clientId 应用id
	 * @param url 指定url
	 */
	public void checkRightUrl(String clientId, String url) {
		// 1、是否是一个有效的url
		if( ! SaFoxUtil.isUrl(url)) {
			throw new SaOAuth2Exception("无效redirect_url：" + url).setCode(SaOAuth2ErrorCode.CODE_30113);
		}

		// 2、截取掉?后面的部分
		int qIndex = url.indexOf("?");
		if(qIndex != -1) {
			url = url.substring(0, qIndex);
		}

		// 3、是否在[允许地址列表]之中
		List<String> allowList = SaFoxUtil.convertStringToList(checkClientModel(clientId).allowUrl);
		if( ! SaStrategy.instance.hasElement.apply(allowList, url)) {
			throw new SaOAuth2Exception("非法redirect_url：" + url).setCode(SaOAuth2ErrorCode.CODE_30114);
		}
	}
	/**
	 * 校验：clientId 与 clientSecret 是否正确
	 * @param clientId 应用id
	 * @param clientSecret 秘钥
	 * @return SaClientModel对象
	 */
	public SaClientModel checkClientSecret(String clientId, String clientSecret) {
		SaClientModel cm = checkClientModel(clientId);
		SaOAuth2Exception.throwBy(cm.clientSecret == null || ! cm.clientSecret.equals(clientSecret),
				"无效client_secret: " + clientSecret, SaOAuth2ErrorCode.CODE_30115);
		return cm;
	}
	/**
	 * 校验：clientId 与 clientSecret 是否正确，并且是否签约了指定 scopes 
	 * @param clientId 应用id
	 * @param clientSecret 秘钥
	 * @param scopes 权限（多个用逗号隔开）
	 * @return SaClientModel对象
	 */
	public SaClientModel checkClientSecretAndScope(String clientId, String clientSecret, String scopes) {
		// 先校验 clientSecret
		SaClientModel cm = checkClientSecret(clientId, clientSecret);
		// 再校验 是否签约 
		List<String> clientScopeList = SaFoxUtil.convertStringToList(cm.contractScope);
		List<String> scopelist = SaFoxUtil.convertStringToList(scopes);
		if( ! clientScopeList.containsAll(scopelist)) {
			throw new SaOAuth2Exception("请求的Scope暂未签约").setCode(SaOAuth2ErrorCode.CODE_30116);
		}
		// 返回数据
		return cm;
	}
	/**
	 * 校验：使用 code 获取 token 时提供的参数校验
	 * @param code 授权码
	 * @param clientId 应用id
	 * @param clientSecret 秘钥
	 * @param redirectUri 重定向地址
	 * @return CodeModel对象
	 */
	public CodeModel checkGainTokenParam(String code, String clientId, String clientSecret, String redirectUri) {

		// 校验：Code是否存在
		CodeModel cm = getCode(code);
		SaOAuth2Exception.throwBy(cm == null, "无效code: " + code, SaOAuth2ErrorCode.CODE_30117);

		// 校验：ClientId是否一致
		SaOAuth2Exception.throwBy( ! cm.clientId.equals(clientId), "无效client_id: " + clientId, SaOAuth2ErrorCode.CODE_30118);

		// 校验：Secret是否正确
		String dbSecret = checkClientModel(clientId).clientSecret;
		SaOAuth2Exception.throwBy(dbSecret == null || ! dbSecret.equals(clientSecret), "无效client_secret: " + clientSecret, SaOAuth2ErrorCode.CODE_30119);

		// 如果提供了redirectUri，则校验其是否与请求Code时提供的一致
		if( ! SaFoxUtil.isEmpty(redirectUri)) {
			SaOAuth2Exception.throwBy( ! redirectUri.equals(cm.redirectUri), "无效redirect_uri: " + redirectUri, SaOAuth2ErrorCode.CODE_30120);
		}

		// 返回CodeModel
		return cm;
	}
	/**
	 * 校验：使用 Refresh-Token 刷新 Access-Token 时提供的参数校验
	 * @param clientId 应用id
	 * @param clientSecret 秘钥
	 * @param refreshToken Refresh-Token
	 * @return CodeModel对象
	 */
	public RefreshTokenModel checkRefreshTokenParam(String clientId, String clientSecret, String refreshToken) {

		// 校验：Refresh-Token是否存在
		RefreshTokenModel rt = getRefreshToken(refreshToken);
		SaOAuth2Exception.throwBy(rt == null, "无效refresh_token: " + refreshToken, SaOAuth2ErrorCode.CODE_30121);

		// 校验：ClientId是否一致
		SaOAuth2Exception.throwBy( ! rt.clientId.equals(clientId), "无效client_id: " + clientId, SaOAuth2ErrorCode.CODE_30122);

		// 校验：Secret是否正确
		String dbSecret = checkClientModel(clientId).clientSecret;
		SaOAuth2Exception.throwBy(dbSecret == null || ! dbSecret.equals(clientSecret), "无效client_secret: " + clientSecret, SaOAuth2ErrorCode.CODE_30123);

		// 返回Refresh-Token
		return rt;
	}
	/**
	 * 校验：Access-Token、clientId、clientSecret 三者是否匹配成功
	 * @param clientId 应用id
	 * @param clientSecret 秘钥
	 * @param accessToken Access-Token
	 * @return SaClientModel对象
	 */
	public AccessTokenModel checkAccessTokenParam(String clientId, String clientSecret, String accessToken) {
		AccessTokenModel at = checkAccessToken(accessToken);
		SaOAuth2Exception.throwBy( ! at.clientId.equals(clientId), "无效client_id：" + clientId, SaOAuth2ErrorCode.CODE_30124);
		checkClientSecret(clientId, clientSecret);
		return at;
	}

	// ------------------- convert 数据转换
	/**
	 * 将 Code 转换为 Access-Token
	 * @param cm CodeModel对象
	 * @return AccessToken对象
	 */
	public AccessTokenModel convertCodeToAccessToken(CodeModel cm) {
		AccessTokenModel at = new AccessTokenModel();
		at.accessToken = randomAccessToken(cm.clientId, cm.loginId, cm.scope);
		// at.refreshToken = randomRefreshToken(cm.clientId, cm.loginId, cm.scope);
		at.clientId = cm.clientId;
		at.loginId = cm.loginId;
		at.scope = cm.scope;
		at.openid = getOpenid(cm.clientId, cm.loginId);
		at.expiresTime = System.currentTimeMillis() + (checkClientModel(cm.clientId).getAccessTokenTimeout() * 1000);
		// at.refreshExpiresTime = System.currentTimeMillis() + (checkClientModel(cm.clientId).getRefreshTokenTimeout() * 1000);
		return at;
	}
	/**
	 * 将 Access-Token 转换为 Refresh-Token
	 * @param at .
	 * @return .
	 */
	public RefreshTokenModel convertAccessTokenToRefreshToken(AccessTokenModel at) {
		RefreshTokenModel rt = new RefreshTokenModel();
		rt.refreshToken = randomRefreshToken(at.clientId, at.loginId, at.scope);
		rt.clientId = at.clientId;
		rt.loginId = at.loginId;
		rt.scope = at.scope;
		rt.openid = at.openid;
		rt.expiresTime = System.currentTimeMillis() + (checkClientModel(at.clientId).getRefreshTokenTimeout() * 1000);
		// 改变at属性
		at.refreshToken = rt.refreshToken;
		at.refreshExpiresTime = rt.expiresTime;
		return rt;
	}
	/**
	 * 将 Refresh-Token 转换为 Access-Token
	 * @param rt .
	 * @return .
	 */
	public AccessTokenModel convertRefreshTokenToAccessToken(RefreshTokenModel rt) {
		AccessTokenModel at = new AccessTokenModel();
		at.accessToken = randomAccessToken(rt.clientId, rt.loginId, rt.scope);
		at.refreshToken = rt.refreshToken;
		at.clientId = rt.clientId;
		at.loginId = rt.loginId;
		at.scope = rt.scope;
		at.openid = rt.openid;
		at.expiresTime = System.currentTimeMillis() + (checkClientModel(rt.clientId).getAccessTokenTimeout() * 1000);
		at.refreshExpiresTime = rt.expiresTime;
		return at;
	}
	/**
	 * 根据 Refresh-Token 创建一个新的 Refresh-Token
	 * @param rt .
	 * @return .
	 */
	public RefreshTokenModel convertRefreshTokenToRefreshToken(RefreshTokenModel rt) {
		RefreshTokenModel newRt = new RefreshTokenModel();
		newRt.refreshToken = randomRefreshToken(rt.clientId, rt.loginId, rt.scope);
		newRt.expiresTime = System.currentTimeMillis() + (checkClientModel(rt.clientId).getRefreshTokenTimeout() * 1000);
		newRt.clientId = rt.clientId;
		newRt.scope = rt.scope;
		newRt.loginId = rt.loginId;
		newRt.openid = rt.openid;
		return newRt;
	}

	// ------------------- save 数据
	/**
	 * 持久化：Code-Model
	 * @param c .
	 */
	public void saveCode(CodeModel c) {
		if(c == null) {
			return;
		}
		SaManager.getSaTokenDao().setObject(splicingCodeSaveKey(c.code), c, SaOAuth2Manager.getConfig().getCodeTimeout());
	}
	/**
	 * 持久化：Code-索引
	 * @param c .
	 */
	public void saveCodeIndex(CodeModel c) {
		if(c == null) {
			return;
		}
		SaManager.getSaTokenDao().set(splicingCodeIndexKey(c.clientId, c.loginId), c.code, SaOAuth2Manager.getConfig().getCodeTimeout());
	}
	/**
	 * 持久化：AccessToken-Model
	 * @param at .
	 */
	public void saveAccessToken(AccessTokenModel at) {
		if(at == null) {
			return;
		}
		SaManager.getSaTokenDao().setObject(splicingAccessTokenSaveKey(at.accessToken), at, at.getExpiresIn());
	}
	/**
	 * 持久化：AccessToken-索引
	 * @param at .
	 */
	public void saveAccessTokenIndex(AccessTokenModel at) {
		if(at == null) {
			return;
		}
		SaManager.getSaTokenDao().set(splicingAccessTokenIndexKey(at.clientId, at.loginId), at.accessToken, at.getExpiresIn());
	}
	/**
	 * 持久化：RefreshToken-Model
	 * @param rt .
	 */
	public void saveRefreshToken(RefreshTokenModel rt) {
		if(rt == null) {
			return;
		}
		SaManager.getSaTokenDao().setObject(splicingRefreshTokenSaveKey(rt.refreshToken), rt, rt.getExpiresIn());
	}
	/**
	 * 持久化：RefreshToken-索引
	 * @param rt .
	 */
	public void saveRefreshTokenIndex(RefreshTokenModel rt) {
		if(rt == null) {
			return;
		}
		SaManager.getSaTokenDao().set(splicingRefreshTokenIndexKey(rt.clientId, rt.loginId), rt.refreshToken, rt.getExpiresIn());
	}
	/**
	 * 持久化：ClientToken-Model
	 * @param ct .
	 */
	public void saveClientToken(ClientTokenModel ct) {
		if(ct == null) {
			return;
		}
		SaManager.getSaTokenDao().setObject(splicingClientTokenSaveKey(ct.clientToken), ct, ct.getExpiresIn());
	}
	/**
	 * 持久化：ClientToken-索引
	 * @param ct .
	 */
	public void saveClientTokenIndex(ClientTokenModel ct) {
		if(ct == null) {
			return;
		}
		SaManager.getSaTokenDao().set(splicingClientTokenIndexKey(ct.clientId), ct.clientToken, ct.getExpiresIn());
	}
	/**
	 * 持久化：Past-Token-索引
	 * @param ct .
	 */
	public void savePastTokenIndex(ClientTokenModel ct) {
		if(ct == null) {
			return;
		}
		long ttl = ct.getExpiresIn();
		SaClientModel cm = checkClientModel(ct.clientId);
		if (cm.getPastClientTokenTimeout() != -1) {
			ttl = cm.getPastClientTokenTimeout();
		}
		SaManager.getSaTokenDao().set(splicingPastTokenIndexKey(ct.clientId), ct.clientToken, ttl);
	}
	/**
	 * 持久化：用户授权记录
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @param scope 权限列表(多个逗号隔开)
	 */
	public void saveGrantScope(String clientId, Object loginId, String scope) {
		if( ! SaFoxUtil.isEmpty(scope)) {
			long ttl = checkClientModel(clientId).getAccessTokenTimeout();
			SaManager.getSaTokenDao().set(splicingGrantScopeKey(clientId, loginId), scope, ttl);
		}
	}

	// ------------------- get 数据
	/**
	 * 获取：Code Model
	 * @param code .
	 * @return .
	 */
	public CodeModel getCode(String code) {
		if(code == null) {
			return null;
		}
		return (CodeModel)SaManager.getSaTokenDao().getObject(splicingCodeSaveKey(code));
	}
	/**
	 * 获取：Code Value
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return .
	 */
	public String getCodeValue(String clientId, Object loginId) {
		return SaManager.getSaTokenDao().get(splicingCodeIndexKey(clientId, loginId));
	}
	/**
	 * 获取：Access-Token Model
	 * @param accessToken .
	 * @return .
	 */
	public AccessTokenModel getAccessToken(String accessToken) {
		if(accessToken == null) {
			return null;
		}
		return (AccessTokenModel)SaManager.getSaTokenDao().getObject(splicingAccessTokenSaveKey(accessToken));
	}
	/**
	 * 获取：Access-Token Value
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return .
	 */
	public String getAccessTokenValue(String clientId, Object loginId) {
		return SaManager.getSaTokenDao().get(splicingAccessTokenIndexKey(clientId, loginId));
	}
	/**
	 * 获取：Refresh-Token Model
	 * @param refreshToken .
	 * @return .
	 */
	public RefreshTokenModel getRefreshToken(String refreshToken) {
		if(refreshToken == null) {
			return null;
		}
		return (RefreshTokenModel)SaManager.getSaTokenDao().getObject(splicingRefreshTokenSaveKey(refreshToken));
	}
	/**
	 * 获取：Refresh-Token Value
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return .
	 */
	public String getRefreshTokenValue(String clientId, Object loginId) {
		return SaManager.getSaTokenDao().get(splicingRefreshTokenIndexKey(clientId, loginId));
	}
	/**
	 * 获取：Client-Token Model
	 * @param clientToken .
	 * @return .
	 */
	public ClientTokenModel getClientToken(String clientToken) {
		if(clientToken == null) {
			return null;
		}
		return (ClientTokenModel)SaManager.getSaTokenDao().getObject(splicingClientTokenSaveKey(clientToken));
	}
	/**
	 * 获取：Client-Token Value
	 * @param clientId 应用id
	 * @return .
	 */
	public String getClientTokenValue(String clientId) {
		return SaManager.getSaTokenDao().get(splicingClientTokenIndexKey(clientId));
	}
	/**
	 * 获取：Past-Token Value
	 * @param clientId 应用id
	 * @return .
	 */
	public String getPastTokenValue(String clientId) {
		return SaManager.getSaTokenDao().get(splicingPastTokenIndexKey(clientId));
	}
	/**
	 * 获取：用户授权记录
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return 权限
	 */
	public String getGrantScope(String clientId, Object loginId) {
		return SaManager.getSaTokenDao().get(splicingGrantScopeKey(clientId, loginId));
	}

	// ------------------- delete数据
	/**
	 * 删除：Code
	 * @param code 值
	 */
	public void deleteCode(String code) {
		if(code != null) {
			SaManager.getSaTokenDao().deleteObject(splicingCodeSaveKey(code));
		}
	}
	/**
	 * 删除：Code索引
	 * @param clientId 应用id
	 * @param loginId 账号id
	 */
	public void deleteCodeIndex(String clientId, Object loginId) {
		SaManager.getSaTokenDao().delete(splicingCodeIndexKey(clientId, loginId));
	}
	/**
	 * 删除：Access-Token
	 * @param accessToken 值
	 */
	public void deleteAccessToken(String accessToken) {
		if(accessToken != null) {
			SaManager.getSaTokenDao().deleteObject(splicingAccessTokenSaveKey(accessToken));
		}
	}
	/**
	 * 删除：Access-Token索引
	 * @param clientId 应用id
	 * @param loginId 账号id
	 */
	public void deleteAccessTokenIndex(String clientId, Object loginId) {
		SaManager.getSaTokenDao().delete(splicingAccessTokenIndexKey(clientId, loginId));
	}
	/**
	 * 删除：Refresh-Token
	 * @param refreshToken 值
	 */
	public void deleteRefreshToken(String refreshToken) {
		if(refreshToken != null) {
			SaManager.getSaTokenDao().deleteObject(splicingRefreshTokenSaveKey(refreshToken));
		}
	}
	/**
	 * 删除：Refresh-Token索引
	 * @param clientId 应用id
	 * @param loginId 账号id
	 */
	public void deleteRefreshTokenIndex(String clientId, Object loginId) {
		SaManager.getSaTokenDao().delete(splicingRefreshTokenIndexKey(clientId, loginId));
	}
	/**
	 * 删除：Client-Token
	 * @param clientToken 值
	 */
	public void deleteClientToken(String clientToken) {
		if(clientToken != null) {
			SaManager.getSaTokenDao().deleteObject(splicingClientTokenSaveKey(clientToken));
		}
	}
	/**
	 * 删除：Client-Token索引
	 * @param clientId 应用id
	 */
	public void deleteClientTokenIndex(String clientId) {
		SaManager.getSaTokenDao().delete(splicingClientTokenIndexKey(clientId));
	}
	/**
	 * 删除：Past-Token索引
	 * @param clientId 应用id
	 */
	public void deletePastTokenIndex(String clientId) {
		SaManager.getSaTokenDao().delete(splicingPastTokenIndexKey(clientId));
	}
	/**
	 * 删除：用户授权记录
	 * @param clientId 应用id
	 * @param loginId 账号id
	 */
	public void deleteGrantScope(String clientId, Object loginId) {
		SaManager.getSaTokenDao().delete(splicingGrantScopeKey(clientId, loginId));
	}

	// ------------------- Random数据
	/**
	 * 随机一个 Code
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @param scope 权限
	 * @return Code
	 */
	public String randomCode(String clientId, Object loginId, String scope) {
		return SaFoxUtil.getRandomString(60);
	}
	/**
	 * 随机一个 Access-Token
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @param scope 权限
	 * @return Access-Token
	 */
	public String randomAccessToken(String clientId, Object loginId, String scope) {
		return SaFoxUtil.getRandomString(60);
	}
	/**
	 * 随机一个 Refresh-Token
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @param scope 权限
	 * @return Refresh-Token
	 */
	public String randomRefreshToken(String clientId, Object loginId, String scope) {
		return SaFoxUtil.getRandomString(60);
	}
	/**
	 * 随机一个 Client-Token
	 * @param clientId 应用id
	 * @param scope 权限
	 * @return Client-Token
	 */
	public String randomClientToken(String clientId, String scope) {
		return SaFoxUtil.getRandomString(60);
	}

	// ------------------- 拼接key
	/**
	 * 拼接key：Code持久化
	 * @param code 授权码
	 * @return key
	 */
	public String splicingCodeSaveKey(String code) {
		return SaManager.getConfig().getTokenName() + ":oauth2:code:" + code;
	}
	/**
	 * 拼接key：Code索引
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return key
	 */
	public String splicingCodeIndexKey(String clientId, Object loginId) {
		return SaManager.getConfig().getTokenName() + ":oauth2:code-index:" + clientId + ":" + loginId;
	}
	/**
	 * 拼接key：Access-Token持久化
	 * @param accessToken accessToken
	 * @return key
	 */
	public String splicingAccessTokenSaveKey(String accessToken) {
		return SaManager.getConfig().getTokenName() + ":oauth2:access-token:" + accessToken;
	}
	/**
	 * 拼接key：Access-Token索引
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return key
	 */
	public String splicingAccessTokenIndexKey(String clientId, Object loginId) {
		return SaManager.getConfig().getTokenName() + ":oauth2:access-token-index:" + clientId + ":" + loginId;
	}
	/**
	 * 拼接key：Refresh-Token持久化
	 * @param refreshToken refreshToken
	 * @return key
	 */
	public String splicingRefreshTokenSaveKey(String refreshToken) {
		return SaManager.getConfig().getTokenName() + ":oauth2:refresh-token:" + refreshToken;
	}
	/**
	 * 拼接key：Refresh-Token索引
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return key
	 */
	public String splicingRefreshTokenIndexKey(String clientId, Object loginId) {
		return SaManager.getConfig().getTokenName() + ":oauth2:refresh-token-index:" + clientId + ":" + loginId;
	}
	/**
	 * 拼接key：Client-Token持久化
	 * @param clientToken clientToken
	 * @return key
	 */
	public String splicingClientTokenSaveKey(String clientToken) {
		return SaManager.getConfig().getTokenName() + ":oauth2:client-token:" + clientToken;
	}
	/**
	 * 拼接key：Client-Token 索引
	 * @param clientId clientId
	 * @return key
	 */
	public String splicingClientTokenIndexKey(String clientId) {
		return SaManager.getConfig().getTokenName() + ":oauth2:client-token-index:" + clientId;
	}
	/**
	 * 拼接key：Past-Token 索引
	 * @param clientId clientId
	 * @return key
	 */
	public String splicingPastTokenIndexKey(String clientId) {
		return SaManager.getConfig().getTokenName() + ":oauth2:past-token-index:" + clientId;
	}
	/**
	 * 拼接key：用户授权记录
	 * @param clientId 应用id
	 * @param loginId 账号id
	 * @return key
	 */
	public String splicingGrantScopeKey(String clientId, Object loginId) {
		return SaManager.getConfig().getTokenName() + ":oauth2:grant-scope:" + clientId + ":" + loginId;
	}

}
