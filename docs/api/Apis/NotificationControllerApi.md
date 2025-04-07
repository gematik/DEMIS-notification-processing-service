# NotificationControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**processNotification**](NotificationControllerApi.md#processNotification) | **POST** /fhir/$process-notification |  |


<a name="processNotification"></a>
# **processNotification**
> Object processNotification(Content-Type, body, X-Request-ID, x-sender, x-testuser, Authorization)



### Parameters

|Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **Content-Type** | [**MediaType**](../Models/.md)|  | [default to null] |
| **body** | **String**|  | |
| **X-Request-ID** | **String**|  | [optional] [default to null] |
| **x-sender** | **String**|  | [optional] [default to null] |
| **x-testuser** | **Boolean**|  | [optional] [default to null] |
| **Authorization** | **String**|  | [optional] [default to null] |

### Return type

**Object**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json, application/json+fhir, application/fhir+json, application/xml, application/xml+fhir, application/fhir+xml, text/xml
- **Accept**: application/json, application/json+fhir, application/fhir+json, application/xml, application/xml+fhir, application/fhir+xml, text/xml

