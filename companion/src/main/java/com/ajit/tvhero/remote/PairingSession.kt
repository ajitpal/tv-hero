package com.ajit.tvhero.remote

import com.ajit.tvhero.protocol.Pairing

/**
 * The phone's half of pairing by code comparison (protocol/Pairing.java). Pure, so it is
 * unit-tested: the TV's commitment must match before any code is shown.
 */
class PairingSession(
    val pairId: String,
    private val myCert: ByteArray,
    val tvCert: ByteArray,
    private val commitment: ByteArray,
    val passwordSet: Boolean,
    val myNonce: ByteArray = Pairing.nonce(),
) {
    /** The code to show, once the TV has revealed its nonce; throws if the TV broke its commitment. */
    fun code(tvNonce: ByteArray): String {
        if (!Pairing.checkCommitment(commitment, tvCert, myCert, tvNonce)) throw TvException(0,
            "The TV's answer didn't match what it promised. Someone may be interfering with your Wi-Fi, so the phone stopped pairing.")
        return Pairing.code(myCert, tvCert, myNonce, tvNonce)
    }

    val tvFingerprint: String get() = Pairing.fingerprint(tvCert)
}
