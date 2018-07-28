package com.open.capacity.security.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.open.capacity.security.dao.OauthClientDetailsDao;
import com.open.capacity.security.dto.OauthClientDetailsDto;
import com.open.capacity.security.model.OauthClientDetails;
import com.open.capacity.security.service.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class ClientServiceImpl implements ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);

    /**
     * 缓存client的redis key，这里是hash结构存储
     */
    private static final String CACHE_CLIENT_KEY = "oauth_client_details";

    @Autowired
    private OauthClientDetailsDao oauthClientDetailsDao;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void saveClient(OauthClientDetailsDto clientDto) {
        OauthClientDetails client = clientDto;
        List<Long> permissionIds = clientDto.getPermissionIds();
        permissionIds.remove(0L);

        if (client.getId() != null) {// 修改
            updateClient(client, permissionIds);
        } else {// 新增
            saveClient(client, permissionIds);
        }
    }

    private void saveClient(OauthClientDetails client, List<Long> permissionIds) {
        OauthClientDetails r = oauthClientDetailsDao.getClient(client.getClientId());
        if (r != null) {
            throw new IllegalArgumentException(client.getClientId() + "已存在");
        }

        oauthClientDetailsDao.save(client);
        if (!CollectionUtils.isEmpty(permissionIds)) {
            oauthClientDetailsDao.saveClientPermission(client.getId(), permissionIds);
        }
        log.debug("新增应用{}", client.getClientId());
    }

    private void updateClient(OauthClientDetails client, List<Long> permissionIds) {
//		OauthClientDetails r = oauthClientDetailsDao.getClient(client.getClientId());
//		if (r != null && r.getId() != client.getId()) {
//			throw new IllegalArgumentException(client.getClientId() + "已存在");
//		}

        oauthClientDetailsDao.update(client);
        oauthClientDetailsDao.deleteClientPermission(client.getId());
        if (!CollectionUtils.isEmpty(permissionIds)) {
            oauthClientDetailsDao.saveClientPermission(client.getId(), permissionIds);
        }

        String clientId = oauthClientDetailsDao.getById(client.getId()).getClientId();
        BaseClientDetails clientDetails = null;

        // 先从redis获取
        try {
            String value = (String) redisTemplate.boundHashOps(CACHE_CLIENT_KEY).get(clientId);
            clientDetails = JSONObject.parseObject(value, BaseClientDetails.class);
            clientDetails.setClientSecret(client.getClientSecret());
            redisTemplate.boundHashOps(CACHE_CLIENT_KEY).put(clientId, JSONObject.toJSONString(clientDetails));
        } catch (Exception e) {

        }
        log.debug("修改应用{}", client.getClientId());
    }

    @Override
    @Transactional
    public void deleteClient(Long id) {
        oauthClientDetailsDao.deleteClientPermission(id);
        oauthClientDetailsDao.delete(id);

        log.debug("删除应用id:{}", id);
    }
}
