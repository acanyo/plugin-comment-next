import { axiosInstance } from '@halo-dev/api-client';

const ENDPOINT =
  '/apis/console.api.commentnext.xhhao.com/v1alpha1/ip-location-backfill';

export type IpLocationBackfillState =
  | 'IDLE'
  | 'RUNNING'
  | 'COMPLETED'
  | 'STOPPED'
  | 'FAILED';

export interface IpLocationBackfillStatus {
  lookupEnabled: boolean;
  progress: {
    state: IpLocationBackfillState;
    saved: number;
    skipped: number;
    unresolved: number;
    failed: number;
  };
}

export async function getIpLocationBackfillStatus(): Promise<IpLocationBackfillStatus> {
  const { data } = await axiosInstance.get<IpLocationBackfillStatus>(ENDPOINT);
  return data;
}

export async function startIpLocationBackfill(): Promise<IpLocationBackfillStatus> {
  const { data } = await axiosInstance.post<IpLocationBackfillStatus>(ENDPOINT);
  return data;
}
