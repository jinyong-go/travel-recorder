import { MOCK_LOGIN_DAILY_STATS } from '../mock/data.js'
import { mockResponse } from './mock.js'

/** 최근 하루 단위 로그인 합계. 백엔드 API 가 없어 목업을 돌려준다. */
export const fetchLoginDailyStats = () => mockResponse(MOCK_LOGIN_DAILY_STATS)
