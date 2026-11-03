package com.firmbanking.domain.consent.mapper;

import com.firmbanking.domain.consent.dto.request.CreateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.request.UpdateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.response.WithdrawalConsentDetailResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WithdrawalConsentMapper {

    WithdrawalConsentDetailResponse findByCompanyNoAndAccountId(@Param("companyNo") String companyNo,
                                                                @Param("accountId") Long accountId);

    void insert(CreateWithdrawalConsentRequest request);

    void updateStatus(UpdateWithdrawalConsentRequest request);
}
