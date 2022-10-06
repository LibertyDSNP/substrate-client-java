package com.strategyobject.substrateclient.api.pallet.transaction;

import com.strategyobject.substrateclient.pallet.annotation.Event;
import com.strategyobject.substrateclient.pallet.annotation.Pallet;
import com.strategyobject.substrateclient.rpc.api.AccountId;
import com.strategyobject.substrateclient.rpc.api.BalanceStatus;
import com.strategyobject.substrateclient.rpc.api.primitives.Balance;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import lombok.Getter;
import lombok.Setter;

@Pallet("TransactionPayment")
public interface TransactionPayment {

    /**
     * An account was created with some free balance. \[account, free_balance\]
     */
    @Event(index = 0)
    @Getter
    @Setter
    @ScaleReader
    class TransactionFeePaid  {
        private AccountId who;
        private Balance actualFee;
        private Balance tip;
    }

}
