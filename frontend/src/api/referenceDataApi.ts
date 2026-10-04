import axiosClient from './axiosClient'
import type { ProvinceDistrictMap } from '../types/destination'

export function getProvinceDistricts() {
  return axiosClient.get<ProvinceDistrictMap>('/reference-data/provinces').then((res) => res.data)
}
