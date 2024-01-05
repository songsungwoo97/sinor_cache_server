package com.sinor.cache.admin.api.service;

import static com.sinor.cache.common.admin.AdminResponseStatus.*;
import static java.nio.charset.StandardCharsets.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sinor.cache.admin.api.model.ApiGetResponse;
import com.sinor.cache.admin.metadata.model.MetadataGetResponse;
import com.sinor.cache.admin.metadata.service.MetadataService;
import com.sinor.cache.common.admin.AdminException;
import com.sinor.cache.utils.JsonToStringConverter;
import com.sinor.cache.utils.RedisUtils;
import com.sinor.cache.utils.URIUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ApiService implements IApiServiceV1 {

	private final JsonToStringConverter jsonToStringConverter;
	private final RedisUtils metadataRedisUtils;
	private final MetadataService metadataService;
	private final RedisUtils responseRedisUtils;

	@Autowired
	public ApiService(MetadataService metadataService, JsonToStringConverter jsonToStringConverter,
		RedisUtils metadataRedisUtils, RedisUtils responseRedisUtils) {
		this.metadataService = metadataService;
		this.metadataRedisUtils = metadataRedisUtils;
		this.jsonToStringConverter = jsonToStringConverter;
		this.responseRedisUtils = responseRedisUtils;
	}

	/**
	 * 캐시 조회
	 * @param key 조회할 캐시의 Key 값
	 */
	public ApiGetResponse findCacheById(String key) throws AdminException {

		String versionKey = URIUtils.getUriPathQuery(key,
			metadataService.findMetadataById(responseRedisUtils.disuniteKey(key)).getVersion());

		String value = responseRedisUtils.getRedisData(versionKey);
		if (value.isBlank())
			throw new AdminException(CACHE_NOT_FOUND);

		return jsonToStringConverter.jsontoClass(value, ApiGetResponse.class);
	}

	/**
	 * 패턴과 일치하는 캐시 조회
	 * @param pattern 조회할 캐시들의 공통 패턴
	 */
	@Override
	public List<ApiGetResponse> findCacheList(String pattern) throws AdminException {
		List<ApiGetResponse> list = new ArrayList<>();

		Cursor<byte[]> cursor = responseRedisUtils.searchPatternKeys(pattern);

		processCursor(cursor, list);

		if (list.isEmpty())
			throw new AdminException(CACHE_NOT_FOUND);

		return list;
	}

	/**
	 * 캐시 생성 및 덮어쓰기
	 * @param key 생성할 캐시의 Key
	 * @param value 생성할 캐시의 Value
	 * @param expiredTime 생성할 캐시의 만료시간
	 */
	@Override
	@Transactional
	public ApiGetResponse saveOrUpdate(String key, String value, Long expiredTime) throws AdminException {
		// path 추출, 해당 path의 metadata 조회
		MetadataGetResponse metadata = metadataService.findMetadataById(responseRedisUtils.disuniteKey(key));
		// 조회한 값을 이용한 Versioning 된 Cache Name 추출
		key = URIUtils.getUriPathQuery(key, metadata.getVersion());

		// 캐시에 저장된 값이 있으면 수정, 없으면 생성
		responseRedisUtils.setRedisData(key, value, expiredTime);

		return jsonToStringConverter.jsontoClass(responseRedisUtils.getRedisData(key), ApiGetResponse.class);
	}

	/**
	 * 캐시 삭제
	 * @param key 삭제할 캐시의 Key
	 */
	@Override
	public Boolean deleteCacheById(String key) throws AdminException {

		String versionKey = URIUtils.getUriPathQuery(key,
			metadataService.findMetadataById(responseRedisUtils.disuniteKey(key)).getVersion());

		log.info("value of deleted key: " + responseRedisUtils.getRedisData(versionKey));
		return responseRedisUtils.deleteCache(versionKey);
	}

	/**
	 * 패턴과 일치하는 캐시 삭제
	 * @param pattern 삭제할 캐시들의 공통 패턴
	 */
	@Override
	public void deleteCacheList(String pattern) throws AdminException {
		// scan으로 키 조회
		Cursor<byte[]> cursor = responseRedisUtils.searchPatternKeys(pattern);

		if (cursor == null)
			throw new AdminException(CACHE_NOT_FOUND);

		// unlink로 키 삭제
		while (cursor.hasNext()) {
			responseRedisUtils.unlinkCache(new String(cursor.next(), UTF_8));
		}
	}

	/**
	 *
	 * @param key 수정할 value의 키 값
	 * @param response 수정내용
	 * @return 수정된 결과값
	 */
	//TODO Redis에서 업데이트 확인, 출력을 위한 역직렬화 과정에서 오류 발생
	@Override
	public ApiGetResponse updateCacheById(String key, String response) {
		// path 추출, 해당 path의 metadata 조회
		MetadataGetResponse metadata = metadataService.findMetadataById(responseRedisUtils.disuniteKey(key));
		// 조회한 값을 이용한 Versioning 된 Cache Name 추출
		key = URIUtils.getUriPathQuery(key, metadata.getVersion());

		if (responseRedisUtils.isExist(key)) {

			// 추출한 Metadata ttl 값으로 캐시 데이터와 변경
			responseRedisUtils.setRedisData(key, response,
				metadata.getMetadataTtlSecond());

			// 변경한 데이터를 추출하여 ApiGetResponse 반환
			return jsonToStringConverter.jsontoClass(responseRedisUtils.getRedisData(key), ApiGetResponse.class);
		}

		throw new AdminException(CACHE_NOT_FOUND);
	}

	/**
	 * RedisTemplate에서 얻은 byte Cursor 값을 CacheGetResponse List 형태로 담아 반환하는 메소드
	 * @param cursor Redis에서 조회로 얻은 Byte 값
	 * @param list cursor를 역직렬화해서 넣어줄 List 객체
	 * @throws AdminException 역직렬화 시 JsonProcessingException이 발생했을 때 Throw될 BaseException
	 */
	private void processCursor(Cursor<byte[]> cursor, List<ApiGetResponse> list) throws AdminException {
		while (cursor.hasNext()) {
			byte[] keyBytes = cursor.next();
			String key = new String(keyBytes, UTF_8);

			String jsonValue = responseRedisUtils.getRedisData(key);
			list.add(jsonToStringConverter.jsontoClass(jsonValue, ApiGetResponse.class));
		}
	}

	// 미사용
	//-----------------------------------------------------------------------------------------------------------------
	/**
	 * 캐시 생성 함수
	 * @param key 캐시 조회 시 활용할 Key 값
	 * @param value 해당 Key에 매칭되는 value 값, 메인 서버에서의 실반환 값이 저장
	 * @param expiredTime 해당 캐시의 만료 시간, Second를 기준으로 계산한다.
	 *//*
	public void setWithExpiration(String key, String value, Long expiredTime) {
		ValueOperations<String, String> ops = redisTemplate.opsForValue();
		ops.set(key, value, expiredTime, TimeUnit.SECONDS);
	}

	*//**
	 * key의 만료 기간 변경 함수 (현재는 하나의 key를 수정하는 형태)
	 * 해당 기능을 살려는 두되, 상위 URL 옵션 값 변경시 캐시를 삭제할 것이기에 실 사용 여부는 의문
	 * @param key 만료 기간 변경할 Key 이름
	 * @param newExpirationTime 새로 적용할 만료기간
	 *//*
	public void updateExpirationTime(String key, long newExpirationTime) {
		// Redis의 EXPIRE 명령을 사용하여 만료 시간을 변경
		redisTemplate.expire(key, newExpirationTime, TimeUnit.SECONDS);
	}

	*//**
	 * 해당 Key를 조회하는 함수
	 * @param key 조회할 캐시의 Key 이름
	 * @return 해당 캐시의 Value 값이 반환 된다.
	 *//*
	public String get(String key) {
		ValueOperations<String, String> ops = redisTemplate.opsForValue();
		return ops.get(key);
	}

	*//**
	 * 특정 키를 찾는 함수
	 * 매개 변수로 받은 Key 값의 패턴의 키 목록을 조회하여 반환
	 * Keys를 사용하는 메서드라 Scan 사용등으로 최소한의 성능 이슈만 처리 필요
	 * @param key 조회할 키들의 패턴 값
	 * @return 패턴에 해당하는 Key들을 Set 형태로 반환
	 *//*
	public Set<String> getKeys(String key) {
		Set<String> keys = redisTemplate.keys("*" + key + "*");
		return keys;
	}

	*//**
	 * 특정 Key의 만료 시간을 조회하는 함수
	 * 캐시가 만료되기까지 남은 시간을 조회한다.
	 * @param key 만료시간을 조회할 Key 이름
	 * @return 해당 Key의 만료 시간
	 *//*
	public Long getExpireTime(String key) {
		return redisTemplate.getExpire(key);
	}

	//sean 코드 병합

	*//**
	 * 특정 문자열을 포함하는 key의 만료 기간 변경 함수
	 * @param keySubstring 만료시간을 조회할 Key 이름
	 * @param newExpirationTime 새로 적용할 만료시간
	 *//*
	@Transactional
	public void updateExpirationTimeByScan(String keySubstring, long newExpirationTime) {

		Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
			connection -> connection.scan(ScanOptions.scanOptions().match("*" + keySubstring + "*").build())
		);

		try {
			while (cursor.hasNext()) {
				byte[] keyBytes = cursor.next();
				String matchingKey = new String(keyBytes, UTF_8);

				redisTemplate.expire(matchingKey, newExpirationTime, TimeUnit.SECONDS);
			}

			System.out.println("문자열을 포함한 TTL 변경 : " + keySubstring);
		} finally {
			cursor.close();
		}
	}

	*//**
	 * 특정 Key를 삭제하는 함수
	 * @param key 삭제할 데이터의 key
	 *//*
	@Transactional
	public void deleteData(String key) {
		redisTemplate.delete(key);
	}

	*//**
	 * 특정 문자열을 포함하는 key를 삭제하는 함수
	 * keys, unlink를 사용
	 * @param searchString 삭제할 키에 포함될 문자열
	 *//*
	@Transactional
	public void deleteDataContainingStringByKeys(String searchString) {
		Set<String> keys = redisTemplate.keys("*" + searchString + "*");

		if (keys != null && !keys.isEmpty()) {
			redisTemplate.executePipelined((RedisCallback<Object>)connection -> {
				for (String key : keys) {
					connection.unlink(key.getBytes(UTF_8));
				}
				return null;
			});

			System.out.println("문자열이 포함된 키 전부 삭제 : " + searchString);
		} else {
			System.out.println("문자열이 포함된 데이터X : " + searchString);
		}
	}

	*//**
	 * 특정 문자열을 포함하는 key를 삭제하는 함수
	 * scan, unlink를 사용
	 * @param searchString 삭제할 키에 포함될 문자열
	 *//*
	@Transactional
	public void deleteDataContainingStringByScan(String searchString) {
		// scan으로 키 조회
		Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
			connection -> {
				ScanOptions options = ScanOptions.scanOptions().match("*" + searchString + "*").build();
				return connection.scan(options);
			});

		try {
			// unlink로 키 삭제
			redisTemplate.executePipelined((RedisCallback<Object>)connection -> {
				while (cursor.hasNext()) {
					byte[] key = cursor.next();
					connection.unlink(key);
				}
				return null;
			});

			System.out.println("관련 키 전부 삭제 : " + searchString);
		} finally {
			cursor.close();
		}
	}

	*//*    @Transactional
	public void deleteDataContainingStringByScan_2(String searchString) {
		Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
				redisConnection -> redisConnection.scan(ScanOptions.scanOptions().match("*" + searchString + "*").build())
		);

		try {
			while (cursor.hasNext()) {
				redisTemplate.unlink(new String(cursor.next(), StandardCharsets.UTF_8));
			}

			System.out.println("문자열이 포함된 키 전부 삭제 : " + searchString);
		} finally {
			cursor.close();
		}
	}
*//*
	public Set<String> searchDataContainingString(String searchString) {
		Set<String> keys = new HashSet<>();

		redisTemplate.executeWithStickyConnection(
			connection -> {
				ScanOptions options = ScanOptions.scanOptions().match("*" + searchString + "*").build();
				Cursor<byte[]> cursor = connection.scan(options);

				while (cursor.hasNext()) {
					byte[] keyBytes = cursor.next();
					String key = new String(keyBytes, UTF_8);
					keys.add(key);
				}

				cursor.close();

				return null;
			});

		return keys;
	}*/
}
