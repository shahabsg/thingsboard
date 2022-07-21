/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.service.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.ota.OtaPackageDao;
import org.thingsboard.server.dao.ota.OtaPackageService;
import org.thingsboard.server.dao.ota.util.ChecksumUtil;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;


import java.sql.SQLException;


import static org.thingsboard.server.common.data.EntityType.OTA_PACKAGE;

@Component
@Slf4j
public class OtaPackageDataValidator extends BaseOtaPackageDataValidator<OtaPackage> {

    @Autowired
    private OtaPackageDao otaPackageDao;

    @Autowired
    @Lazy
    private TbTenantProfileCache tenantProfileCache;

    @Override
    protected void validateCreate(TenantId tenantId, OtaPackage otaPackage) {
        DefaultTenantProfileConfiguration profileConfiguration =
                (DefaultTenantProfileConfiguration) tenantProfileCache.get(tenantId).getProfileData().getConfiguration();
        long maxOtaPackagesInBytes = profileConfiguration.getMaxOtaPackagesInBytes();
        validateMaxSumDataSizePerTenant(tenantId, otaPackageDao, maxOtaPackagesInBytes, otaPackage.getDataSize(), OTA_PACKAGE);
    }

    @Override
    protected void validateDataImpl(TenantId tenantId, OtaPackage otaPackage) {
        validateImpl(otaPackage);

        if (!otaPackage.hasUrl()) {
            if (StringUtils.isEmpty(otaPackage.getFileName())) {
                throw new DataValidationException("OtaPackage file name should be specified!");
            }

            if (StringUtils.isEmpty(otaPackage.getContentType())) {
                throw new DataValidationException("OtaPackage content type should be specified!");
            }

            if (otaPackage.getChecksumAlgorithm() == null) {
                throw new DataValidationException("OtaPackage checksum algorithm should be specified!");
            }
            if (StringUtils.isEmpty(otaPackage.getChecksum())) {
                throw new DataValidationException("OtaPackage checksum should be specified!");
            }
            try {
                String currentChecksum = ChecksumUtil.generateChecksum(otaPackage.getChecksumAlgorithm(), otaPackage.getData().getBinaryStream());
                if (!currentChecksum.equals(otaPackage.getChecksum())) {
                    throw new DataValidationException("Wrong otaPackage file!");
                }
            }catch (SQLException e){
                log.error("Failed to check ota package data {}",otaPackage.getId(), e);
                throw new RuntimeException("otaPackage file can't be validated");
            }
        } else {
            if (otaPackage.getData() != null) {
                throw new DataValidationException("File can't be saved if URL present!");
            }
        }
    }

    @Override
    protected void validateUpdate(TenantId tenantId, OtaPackage otaPackage) {
        OtaPackage otaPackageOld = otaPackageDao.findById(tenantId, otaPackage.getUuidId());

        validateUpdate(otaPackage, otaPackageOld);

        if (otaPackageOld.getData() != null && !otaPackageOld.getData().equals(otaPackage.getData())) {
            throw new DataValidationException("Updating otaPackage data is prohibited!");
        }

        if (otaPackageOld.getData() == null && otaPackage.getData() != null) {
            DefaultTenantProfileConfiguration profileConfiguration =
                    (DefaultTenantProfileConfiguration) tenantProfileCache.get(tenantId).getProfileData().getConfiguration();
            long maxOtaPackagesInBytes = profileConfiguration.getMaxOtaPackagesInBytes();
            validateMaxSumDataSizePerTenant(tenantId, otaPackageDao, maxOtaPackagesInBytes, otaPackage.getDataSize(), OTA_PACKAGE);
        }
    }
}
