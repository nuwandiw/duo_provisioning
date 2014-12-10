/*
 *  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.identity.provisioning.connector.duo;

import com.duosecurity.client.Http;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.*;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class DuoProvisioningConnector extends AbstractOutboundProvisioningConnector {

    private static final long serialVersionUID = 8465869197181038416L;

    private static final Log log = LogFactory.getLog(DuoProvisioningConnector.class);
    private DuoProvisioningConnectorConfig configHolder;

    @Override
    /**
     * 
     */
    public void init(Property[] provisioningProperties) throws IdentityProvisioningException {
        Properties configs = new Properties();

        if (provisioningProperties != null && provisioningProperties.length > 0) {
            for (Property property : provisioningProperties) {
                configs.put(property.getName(), property.getValue());
                if (IdentityProvisioningConstants.JIT_PROVISIONING_ENABLED.equals(property
                        .getName())) {
                    if ("1".equals(property.getValue())) {
                        jitProvisioningEnabled = true;
                    }
                }
            }
        }

        configHolder = new DuoProvisioningConnectorConfig(configs);
    }

    @Override
    /**
     * 
     */
    public ProvisionedIdentifier provision(ProvisioningEntity provisioningEntity)
            throws IdentityProvisioningException {
        String provisionedId = null;

        if (provisioningEntity != null) {

            if (provisioningEntity.isJitProvisioning() && !isJitProvisioningEnabled()) {
                log.debug("JIT provisioning disabled for Duo connector");
                return null;
            }

            if (provisioningEntity.getEntityType() == ProvisioningEntityType.USER) {
                if (provisioningEntity.getOperation() == ProvisioningOperation.DELETE) {
                    deleteUser(provisioningEntity);
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.POST) {
                    provisionedId = createUser(provisioningEntity);
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.PUT) {
                    updateUser(provisioningEntity);
                } else {
                    log.warn("Unsupported provisioning opertaion.");
                }
            } else {
                log.warn("Unsupported provisioning opertaion.");
            }
        }

        // creates a provisioned identifier for the provisioned user.
        ProvisionedIdentifier identifier = new ProvisionedIdentifier();
        identifier.setIdentifier(provisionedId);
        return identifier;
    }

    /**
     * 
     * @param provisioningEntity
     * @return provisionedId
     * @throws IdentityProvisioningException
     */
    private String createUser(ProvisioningEntity provisioningEntity)
            throws IdentityProvisioningException {

        boolean isDebugEnabled = log.isDebugEnabled();
        Object result = null;
        JSONObject jo = null;
        String provisionedId = null;

        Map<String, String> requiredAttributes = getSingleValuedClaims(provisioningEntity
                .getAttributes());
        requiredAttributes.put(DuoConnectorConstants.USERNAME, provisioningEntity.getEntityName());

        try {
            result = httpCall("POST", DuoConnectorConstants.API_USER, requiredAttributes);

        } catch (UnsupportedEncodingException e) {
            log.error("Error in encoding provisioning request");
            throw new IdentityProvisioningException(e);
        } catch(JSONException e){
            log.error("JSON exception");
        }catch (Exception e){
            log.error("Error occured. User not created in Duo");
            System.out.println(e.getMessage());
        }


        try {
            jo = new JSONObject(result.toString());
            provisionedId = jo.getString(DuoConnectorConstants.USER_ID);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (isDebugEnabled) {
            log.debug("Returning created user's ID : " + provisionedId);
        }

        return provisionedId;
    }

    /**
     * 
     * @param provisioningEntity
     * @throws IdentityProvisioningException
     */
    private void deleteUser(ProvisioningEntity provisioningEntity)
            throws IdentityProvisioningException {

        String user_id = null;

        try {
            user_id = getIdbyUsername(provisioningEntity.getEntityName().toLowerCase());
            if(user_id != null){
                httpCall("DELETE", DuoConnectorConstants.API_USER+"/"+user_id, null);
            }else{
                log.info("User doesn't exist in Duo");
            }

        } catch (Exception e) {
            log.error("Error while deleting user from Duo");
            throw new IdentityProvisioningException(e);
        }
    }


    /**
     *
     * @param uname
     * @return
     */
    private String getIdbyUsername(String uname){
        Object result = null;
        String user_id = null;
        JSONArray jo;

        Map<String, String> param = new HashMap<String, String>();

        try{
            param.put(DuoConnectorConstants.USERNAME, uname);
            result = httpCall("GET", DuoConnectorConstants.API_USER, param);

        }catch (UnsupportedEncodingException e) {
            log.error("Error in retrieving user");
        } catch(JSONException e){
            log.error("JSON exception");
        }catch (Exception e){
            log.error("Exception while retieving data");
        }


        try {
            jo = new JSONArray(result.toString());
            if(jo.length() > 0){
                user_id = jo.getJSONObject(0).getString(DuoConnectorConstants.USER_ID);
            }


        } catch (JSONException e) {
            log.error("JSON exception");
            e.printStackTrace();
        }
        return user_id;
    }

    /**
     *
     * @param provisioningEntity
     */
    private void updateUser(ProvisioningEntity provisioningEntity){
        String phone_id = null;
        String user_id = null;
        String name = null;
        String email = null;
        String phone_number = null;

        Map<String, String> requiredAttributes = getSingleValuedClaims(provisioningEntity.getAttributes());


        if(requiredAttributes.get(DuoConnectorConstants.PHONE_NUMBER) == null
                && requiredAttributes.get(DuoConnectorConstants.REAL_NAME)==null
                && requiredAttributes.get(DuoConnectorConstants.EMAIL)==null){
            return;
        }

        user_id = getIdbyUsername(provisioningEntity.getEntityName());
        phone_number = requiredAttributes.get(DuoConnectorConstants.PHONE_NUMBER);

        modifyDuoUser(user_id, requiredAttributes);

        if(phone_number != null &&  phone_number.trim().length() > 0){
            addPhoneToUser(user_id,phone_number);
        }

    }

    /**
     *
     * @param phone
     * @return
     */
    private String createPhone(Map<String,String> phone){
        String phone_id = null;
        Object result = null;
        JSONObject jo = null;



        try {
            result = httpCall("POST", DuoConnectorConstants.API_PHONE, phone);

        }catch (UnsupportedEncodingException e){
            log.error("Could not add device in Duo");
            e.printStackTrace();

        }catch (Exception e){
            log.error("Could not add device in Duo");
            e.printStackTrace();
        }

        try {
            jo = new JSONObject(result.toString());
            phone_id = jo.getString(DuoConnectorConstants.PHONE_ID);
        } catch (JSONException e) {
            log.error("JSON error");
            e.printStackTrace();
        }

        return  phone_id;

    }

    /**
     *
     * @param userId
     * @param atts
     */
    private void modifyDuoUser(String userId, Map<String, String> atts){
        Object result;
        if(atts.get(DuoConnectorConstants.PHONE_NUMBER) != null){
            atts.remove(DuoConnectorConstants.PHONE_NUMBER);
        }
        try {
            result = httpCall("POST", DuoConnectorConstants.API_USER+"/"+userId, atts);
        } catch (UnsupportedEncodingException e) {
            log.error("User not modified in Duo");
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }

        return;
    }

    /**
     *
     * @param userId
     * @param phone
     * @return
     */
    private String addPhoneToUser(String userId, String phone){
        Object result = null;
        String phone_id = null;

        Map<String,String> param = new HashMap<String, String>();
        param.put(DuoConnectorConstants.PHONE_NUMBER, phone);

        phone_id = getPhoneByNumber(param);

        if(phone_id == null){
            phone_id = createPhone(param);
        }
        param.remove(DuoConnectorConstants.PHONE_NUMBER);
        param.put(DuoConnectorConstants.PHONE_ID, phone_id);

        try {
            result = httpCall("POST", DuoConnectorConstants.API_USER+"/"+userId+"/phones", param);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    /**
     *
     * @param phone
     * @return
     */
    private String getPhoneByNumber(Map<String, String> phone){
        Object result = null;
        String phone_id = null;
        JSONArray jo = null;

        try {
            result = httpCall("GET", DuoConnectorConstants.API_PHONE, phone);

        } catch (UnsupportedEncodingException e) {
            log.error("Error while retrieving phone ID");
            System.out.println(e.getMessage());
            return null;
        } catch (Exception e){
            log.error("Error while retrieving phone ID");
            System.out.println(e.getMessage());
            return null;
        }

        try {
            jo = new JSONArray(result.toString());
            if(jo.length() > 0){
                phone_id = jo.getJSONObject(0).getString(DuoConnectorConstants.PHONE_ID);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return phone_id;
    }

    /**
     *
     * @param method
     * @param URI
     * @param param
     * @return
     * @throws Exception
     */
    private Object httpCall(String method,String URI, Map param) throws Exception {
        Object result = null;

        Http request = new Http(method,configHolder.getValue(DuoConnectorConstants.HOST),URI);

        if(param != null){
            Iterator<Map.Entry<String, String>> iterator = param.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, String> mapEntry = iterator.next();
                if(mapEntry.getValue() != null){
                    request.addParam(mapEntry.getKey(),mapEntry.getValue());
                }
            }

        }


        request.signRequest(configHolder.getValue(DuoConnectorConstants.IKEY),configHolder.getValue(DuoConnectorConstants.SKEY));
        result = request.executeRequest();

        return result;
    }


}
