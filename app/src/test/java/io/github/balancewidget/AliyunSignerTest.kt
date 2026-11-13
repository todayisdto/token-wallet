package io.github.balancewidget

import org.junit.Assert.assertEquals
import org.junit.Test

class AliyunSignerTest {

    @Test
    fun matchesAliyunRpcSignatureExample() {
        val params = linkedMapOf(
            "AccessKeyId" to "testid",
            "Action" to "DescribeRegions",
            "Format" to "XML",
            "SignatureMethod" to "HMAC-SHA1",
            "SignatureNonce" to "3ee8c1b8-83d3-44af-a94f-4e0ad82fd6cf",
            "SignatureVersion" to "1.0",
            "Timestamp" to "2016-02-23T12:46:24Z",
            "Version" to "2014-05-26"
        )

        assertEquals(
            "OLeaidS1JvxuMvnyHOwuJ+uX5qY=",
            AliyunSigner.sign(params, "testsecret")
        )
    }

    @Test
    fun percentEncodingFollowsAliyunRules() {
        assertEquals("a%20b%2A~", AliyunSigner.percentEncode("a b*~"))
        assertEquals("%2F", AliyunSigner.percentEncode("/"))
    }
}
