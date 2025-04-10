package com.august.viiin.model

import com.google.gson.annotations.SerializedName

data class LookupResponse(
    val code: String,
    val total: Int,
    val offset: Int,
    val items: List<LookupItem>
)

data class LookupItem(
    val ean: String?,
    val title: String?,
    val description: String?,
    val upc: String?,
    val brand: String?,
    val model: String?,
    val color: String?,
    val size: String?,
    val dimension: String?,
    val weight: String?,
    val category: String?,
    val currency: String?,
    @SerializedName("lowest_recorded_price") val lowestRecordedPrice: Double?,
    @SerializedName("highest_recorded_price") val highestRecordedPrice: Double?,
    val images: List<String>?,
    val offers: List<Offer>?,
    val elid: String?
)

data class Offer(
    val merchant: String?,
    val domain: String?,
    val title: String?,
    val currency: String?,
    @SerializedName("list_price") val listPrice: Double?,
    val price: Double?,
    val shipping: String?,
    val condition: String?,
    val availability: String?,
    val link: String?,
    @SerializedName("updated_t") val updatedTimestamp: Long?
)