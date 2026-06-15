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
}

const ANONYMOUS_USER_NAME = "anonymousUser";

export async function fetchCurrentUser(baseUrl = ""): Promise<CurrentUser | undefined> {
  const response = await fetch(`${baseUrl}/apis/api.console.halo.run/v1alpha1/users/-`, {
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch current user: ${response.status}`);
  }

  const data = (await response.json()) as DetailedUserResponse;
  const user = data.user;
  const name = user?.metadata?.name ?? "";

  if (!user || name === ANONYMOUS_USER_NAME) {
    return undefined;
  }

  return {
    name,
    displayName: user.spec?.displayName || name,
    avatar: user.spec?.avatar,
  };
}
