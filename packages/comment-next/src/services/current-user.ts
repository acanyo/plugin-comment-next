import { resolveApiUrl } from './api';

export interface CurrentUser {
  name: string;
  displayName: string;
  avatar?: string;
}

interface DetailedUserResponse {
  user?: {
    metadata?: {
      name?: string;
    };
    spec?: {
      displayName?: string;
      avatar?: string;
    };
  };
  metadata?: {
    name?: string;
  };
  spec?: {
    displayName?: string;
    avatar?: string;
  };
}

const ANONYMOUS_USER_NAME = 'anonymousUser';
const CURRENT_USER_ENDPOINTS = [
  '/apis/api.halo.run/v1alpha1/users/-',
  '/apis/api.console.halo.run/v1alpha1/users/-',
];

export async function fetchCurrentUser(
  baseUrl = ''
): Promise<CurrentUser | undefined> {
  for (const endpoint of CURRENT_USER_ENDPOINTS) {
    const user = await fetchCurrentUserFrom(baseUrl, endpoint);

    if (user) {
      return user;
    }
  }

  return undefined;
}

async function fetchCurrentUserFrom(
  baseUrl: string,
  endpoint: string
): Promise<CurrentUser | undefined> {
  const response = await fetch(resolveApiUrl(baseUrl, endpoint), {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    return undefined;
  }

  const data = (await response.json()) as DetailedUserResponse;
  const user = data.user ?? data;
  const name = user?.metadata?.name ?? '';

  if (!user || name === ANONYMOUS_USER_NAME) {
    return undefined;
  }

  return {
    name,
    displayName: user.spec?.displayName || name,
    avatar: user.spec?.avatar,
  };
}
